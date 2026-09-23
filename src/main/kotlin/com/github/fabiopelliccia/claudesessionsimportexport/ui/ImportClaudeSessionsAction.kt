package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.github.fabiopelliccia.claudesessionsimportexport.core.FileImportLog
import com.github.fabiopelliccia.claudesessionsimportexport.core.ImportEnvironment
import com.github.fabiopelliccia.claudesessionsimportexport.core.ImportOutcome
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionArchive
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionScanner
import com.github.fabiopelliccia.claudesessionsimportexport.core.TransferProgress
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Tools | Claude Code sessions | Import Sessions...
 *
 * Built for moving sessions to a different machine, and possibly a different user, as much as for
 * restoring your own: the dialog offers to attach the sessions to a local folder (pre-filled with
 * the open project), because the recorded project path is meaningless anywhere else. Every
 * timestamp is shifted so a session lands around "now" instead of when it was recorded, and a
 * session already present is imported under a new id by default ([ConflictPolicy.DUPLICATE]), so
 * importing an archive twice is always safe.
 */
class ImportClaudeSessionsAction : ClaudeSessionActionBase(
    textKey = "action.ClaudeSessionsImportExport.Import.text",
    descriptionKey = "action.ClaudeSessionsImportExport.Import.description",
) {

    companion object {
        private const val LOG_SUBDIR = "claude-sessions-import"
        private const val LOGS_TO_KEEP = 20
        private val LOG_FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /** Matches the `<version>` element `patchPluginXml` stamps into the bundled `plugin.xml`. */
        private val VERSION_TAG = Regex("<version>([^<]*)</version>")
    }

    override val requiresExistingHome: Boolean = false

    override fun perform(project: Project?, home: Path) {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withExtensionFilter(SessionArchive.ARCHIVE_EXTENSION)
            .withTitle(ClaudeSessionsBundle.message("dialog.import.chooser.title"))
            .withDescription(ClaudeSessionsBundle.message("dialog.import.chooser.description"))
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        val archive: Path = chosen.toNioPath()

        // Both reads happen under the same modal progress: the dialog then only needs the two
        // values, and no file system access is left on the EDT.
        val preview = runWithProgress(project, ClaudeSessionsBundle.message("progress.readingArchive")) {
            SessionArchive.readManifest(archive) to SessionScanner.existingTranscripts(home).keys
        } ?: return
        val (sessions, existingIds) = preview
        if (sessions.isEmpty()) {
            Messages.showInfoMessage(project, ClaudeSessionsBundle.message("dialog.import.empty"), TITLE)
            return
        }

        val dialog = ImportSessionsDialog(project, archive, sessions, existingIds)
        if (!dialog.showAndGet()) return
        val selectedIds = dialog.selectedSessions().map { it.id }.toSet()
        if (selectedIds.isEmpty()) return
        val policy = dialog.conflictPolicy()
        val relocateTo = dialog.relocationTarget()

        // Always written, with no option to disable it: the log of the one import that actually
        // matters - the one that goes wrong - must not depend on the user remembering to turn on
        // some diagnostic flag beforehand.
        val logDir = Paths.get(PathManager.getLogPath(), LOG_SUBDIR)
        val logFile = logDir.resolve("import-${LOG_FILE_STAMP.format(LocalDateTime.now())}.log")
        val log = FileImportLog(logFile)

        val title = ClaudeSessionsBundle.message("progress.importingSessions")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val progress = TransferProgress { fraction, text ->
                    indicator.checkCanceled()
                    indicator.fraction = fraction
                    indicator.text2 = text
                }
                val outcome = SessionArchive.import(
                    archive,
                    sessionIds = selectedIds,
                    conflictPolicy = policy,
                    targetProjectPath = relocateTo,
                    shiftTimestampsToNow = true,
                    home = home,
                    progress = progress,
                    log = log,
                    environment = pluginEnvironment(),
                )
                notifyOutcome(project, outcome, logFile)
            }

            override fun onThrowable(error: Throwable) {
                log.section("Failure")
                log.failure("Import failed before completion", error)
                ClaudeNotifications.error(
                    project,
                    ClaudeSessionsBundle.message("notification.import.failed.title"),
                    StringUtil.escapeXmlEntities(error.message ?: error.javaClass.simpleName) +
                        "<br/>" + ClaudeSessionsBundle.message("notification.import.log", logFile),
                    ClaudeNotifications.showLogAction(logFile),
                )
            }

            override fun onFinished() {
                // Closed here rather than in a try/finally inside run(): onFinished runs exactly
                // once whether run() completed or onThrowable took over, so this is the one place
                // that reliably sees both outcomes.
                log.close()
                rotateLogs(logDir)
            }
        })
    }

    private fun notifyOutcome(project: Project?, outcome: ImportOutcome, logFile: Path) {
        val failedChecks = outcome.failedChecks
        val details = buildString {
            append(ClaudeSessionsBundle.message("notification.import.imported", outcome.imported.size))
            val duplicated = outcome.imported.count { it.action == "duplicated" }
            val replaced = outcome.imported.count { it.action == "replaced" }
            if (duplicated > 0) append(", " + ClaudeSessionsBundle.message("notification.import.duplicated", duplicated))
            if (replaced > 0) append(", " + ClaudeSessionsBundle.message("notification.import.replaced", replaced))
            if (outcome.skipped.isNotEmpty()) {
                append(", " + ClaudeSessionsBundle.message("notification.import.skipped", outcome.skipped.size))
            }
            if (outcome.failures.isNotEmpty()) {
                append(", " + ClaudeSessionsBundle.message("notification.import.failures", outcome.failures.size))
                outcome.failures.forEach { append("<br/>${it.sessionId}: ${StringUtil.escapeXmlEntities(it.message)}") }
            }
            // What Claude Code will really find, re-read from disk by the import.
            for (session in outcome.imported) {
                append(
                    "<br/>" + ClaudeSessionsBundle.message(
                        "notification.import.session",
                        StringUtil.escapeXmlEntities(session.displayName),
                        StringUtil.escapeXmlEntities(session.cwd ?: ClaudeSessionsBundle.message("notification.import.noFolder")),
                        session.messageCount,
                    ),
                )
            }
            for (check in failedChecks) {
                val name = outcome.imported.firstOrNull { it.writtenId == check.sessionId }?.displayName ?: check.sessionId
                append(
                    "<br/>&#9888; " + ClaudeSessionsBundle.message(
                        "notification.import.problem.check",
                        StringUtil.escapeXmlEntities(name),
                        check.number,
                        ClaudeSessionsBundle.message("diagnosis.check.${check.number}"),
                    ),
                )
            }
            outcome.warnings.forEach { append("<br/>&#9888; ${StringUtil.escapeXmlEntities(it)}") }
            if (outcome.imported.isNotEmpty()) {
                append("<br/>" + ClaudeSessionsBundle.message("notification.import.resumeHint"))
            }
            append("<br/>" + ClaudeSessionsBundle.message("notification.import.log", logFile))
        }
        val actions = buildList {
            if (outcome.imported.isNotEmpty()) {
                val command = outcome.imported.singleOrNull()?.let { "claude --resume ${it.writtenId}" } ?: "claude --resume"
                add(ClaudeNotifications.copyResumeAction(command))
            }
            add(ClaudeNotifications.showLogAction(logFile))
        }.toTypedArray<NotificationAction>()

        if (outcome.failures.isEmpty() && failedChecks.isEmpty() && outcome.warnings.isEmpty()) {
            ClaudeNotifications.info(project, ClaudeSessionsBundle.message("notification.import.success.title"), details, *actions)
        } else {
            ClaudeNotifications.warn(project, ClaudeSessionsBundle.message("notification.import.warnings.title"), details, *actions)
        }
    }

    private fun pluginEnvironment(): ImportEnvironment = ImportEnvironment(
        pluginVersion = ownPluginVersion(),
        ideBuild = runCatching { ApplicationInfo.getInstance().build.asString() }.getOrNull(),
        productName = runCatching { ApplicationNamesInfo.getInstance().fullProductName }.getOrNull(),
    )

    /**
     * Reads this plugin's own version straight from the `<version>` element `patchPluginXml`
     * stamps into the bundled `META-INF/plugin.xml`, so describing it in the log needs no
     * `PluginManager` lookup - and no internal API - at all.
     */
    private fun ownPluginVersion(): String? = runCatching {
        javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?.let { VERSION_TAG.find(it)?.groupValues?.get(1) }
    }.getOrNull()

    /** Keeps the log folder from growing forever: only the most recent [LOGS_TO_KEEP] files survive. */
    private fun rotateLogs(dir: Path, keep: Int = LOGS_TO_KEEP) {
        runCatching {
            if (!Files.isDirectory(dir)) return
            val files = Files.newDirectoryStream(dir, "import-*.log").use { it.toList() }.sortedBy { it.fileName.toString() }
            if (files.size > keep) {
                files.take(files.size - keep).forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }
}
