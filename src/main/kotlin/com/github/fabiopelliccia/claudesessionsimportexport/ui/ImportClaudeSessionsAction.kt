package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudePaths
import com.github.fabiopelliccia.claudesessionsimportexport.core.ConflictPolicy
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionArchive
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionScanner
import com.github.fabiopelliccia.claudesessionsimportexport.core.TransferProgress
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

/**
 * Tools | Claude Code sessions | Import Sessions...
 *
 * Built for moving sessions to a different machine, and possibly a different user, as much as for
 * restoring your own: the archive's recorded project path is meaningless anywhere else, so the
 * dialog always asks which local folder the sessions should be attached to (pre-filled with the
 * open project, when there is one). Every timestamp is shifted so the session lands around "now"
 * instead of when it was originally recorded - it shows up as a session that just happened, not an
 * old one. A session already present locally is never overwritten: it is imported under a freshly
 * generated id instead ([ConflictPolicy.DUPLICATE]), so importing an archive twice is always safe.
 */
class ImportClaudeSessionsAction : ClaudeSessionActionBase() {

    override fun perform(project: Project?) {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withExtensionFilter("zip")
            .withTitle("Import Claude Code Sessions")
            .withDescription("Choose a Claude Code sessions archive")
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        val archive: Path = chosen.toNioPath()

        val preview = runWithProgress(project, "Reading archive…") {
            SessionArchive.readManifest(archive) to SessionScanner.listSessions().map { it.id }.toSet()
        } ?: return
        val (manifestSessions, existingIds) = preview
        if (manifestSessions.isEmpty()) {
            Messages.showInfoMessage(project, "The archive contains no sessions.", TITLE)
            return
        }

        val dialog = SessionCheckboxListDialog(
            project,
            "Import Claude Code Sessions",
            manifestSessions,
            alreadyPresentIds = existingIds,
            initiallyChecked = true,
            showTargetPathField = true,
            targetPathDefault = project?.basePath ?: "",
        )
        if (!dialog.showAndGet()) return
        val selectedIds = dialog.selectedSessions.map { it.id }.toSet()
        if (selectedIds.isEmpty()) return
        val targetProjectPath = dialog.targetProjectPath

        object : Task.Backgroundable(project, "Importing Claude Code Sessions", true) {
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
                    conflictPolicy = ConflictPolicy.DUPLICATE,
                    targetProjectPath = targetProjectPath,
                    shiftTimestampsToNow = true,
                    progress = progress,
                )
                val details = buildString {
                    append("${outcome.imported.size} session(s) imported into ${ClaudePaths.projectsDir()}.")
                    val duplicated = outcome.imported.count { it.action == "duplicated" }
                    if (duplicated > 0) append("\n$duplicated of them were already present and got a new id.")
                    outcome.warnings.forEach { append("\n⚠ $it") }
                }
                if (outcome.warnings.isEmpty()) {
                    ClaudeNotifications.info(project, "Import completed", details)
                } else {
                    ClaudeNotifications.warn(project, "Import completed with warnings", details)
                }
            }

            override fun onThrowable(error: Throwable) {
                ClaudeNotifications.error(project, "Import failed", error.message ?: error.javaClass.simpleName)
            }
        }.queue()
    }
}
