package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionArchive
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionScanner
import com.github.fabiopelliccia.claudesessionsimportexport.core.TransferProgress
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Tools | Claude Code sessions | Export Sessions... */
class ExportClaudeSessionsAction : ClaudeSessionActionBase(
    textKey = "action.export.text",
    descriptionKey = "action.export.description",
) {

    override fun perform(project: Project?, home: Path) {
        val sessions = runWithProgress(project, ClaudeSessionsBundle.message("progress.readingSessions")) {
            SessionScanner.listSessions(home)
        } ?: return
        if (sessions.isEmpty()) {
            Messages.showInfoMessage(project, ClaudeSessionsBundle.message("dialog.export.empty"), TITLE)
            return
        }

        val dialog = ExportSessionsDialog(project, sessions)
        if (!dialog.showAndGet()) return
        val selected = dialog.selectedSessions()
        if (selected.isEmpty()) return

        val descriptor = FileSaverDescriptor(
            ClaudeSessionsBundle.message("dialog.export.title"),
            ClaudeSessionsBundle.message("dialog.export.chooser.description"),
            SessionArchive.ARCHIVE_EXTENSION,
        )
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val wrapper = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(null as VirtualFile?, "claude-sessions-$timestamp.zip")
            ?: return
        val destination = wrapper.file.toPath()

        val title = ClaudeSessionsBundle.message("progress.exportingSessions")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val progress = TransferProgress { fraction, text ->
                    indicator.checkCanceled()
                    indicator.fraction = fraction
                    indicator.text2 = text
                }
                val outcome = SessionArchive.export(selected, destination, home = home, progress = progress)
                val details = buildString {
                    append(ClaudeSessionsBundle.message("notification.export.written", outcome.exportedCount, destination.toAbsolutePath()))
                    outcome.warnings.forEach { append("<br/>&#9888; ${StringUtil.escapeXmlEntities(it)}") }
                    // Only when a file really could not be read: that is the one case closing the
                    // session and exporting again actually fixes.
                    if (outcome.unreadableFiles > 0) {
                        append("<br/>" + ClaudeSessionsBundle.message("notification.export.unreadableFiles"))
                    }
                }
                val action = ClaudeNotifications.showArchiveAction(destination)
                if (outcome.warnings.isEmpty()) {
                    ClaudeNotifications.info(project, ClaudeSessionsBundle.message("notification.export.success.title"), details, action)
                } else {
                    ClaudeNotifications.warn(project, ClaudeSessionsBundle.message("notification.export.warnings.title"), details, action)
                }
            }

            override fun onThrowable(error: Throwable) {
                ClaudeNotifications.error(
                    project,
                    ClaudeSessionsBundle.message("notification.export.failed.title"),
                    StringUtil.escapeXmlEntities(error.message ?: error.javaClass.simpleName),
                )
            }
        })
    }
}
