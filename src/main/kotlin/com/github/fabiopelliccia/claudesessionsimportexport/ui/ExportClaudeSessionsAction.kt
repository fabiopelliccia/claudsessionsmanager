package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionArchive
import com.github.fabiopelliccia.claudesessionsimportexport.core.SessionScanner
import com.github.fabiopelliccia.claudesessionsimportexport.core.TransferProgress
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Tools | Claude Code sessions | Export Sessions... */
class ExportClaudeSessionsAction : ClaudeSessionActionBase() {

    override fun perform(project: Project?) {
        val sessions = runWithProgress(project, "Reading Claude Code sessions…") {
            SessionScanner.listSessions()
        } ?: return

        if (sessions.isEmpty()) {
            Messages.showInfoMessage(project, "No local Claude Code sessions were found.", TITLE)
            return
        }

        val dialog = SessionCheckboxListDialog(
            project,
            "Export Claude Code Sessions",
            sessions,
            alreadyPresentIds = emptySet(),
            initiallyChecked = false,
        )
        if (!dialog.showAndGet()) return
        val selected = dialog.selectedSessions
        if (selected.isEmpty()) return

        val descriptor = FileSaverDescriptor(
            "Export Claude Code Sessions",
            "Choose where to save the archive",
            "zip",
        )
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val wrapper = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(null as VirtualFile?, "claude-sessions-$timestamp.zip")
            ?: return
        val destination = wrapper.file.toPath()

        object : Task.Backgroundable(project, "Exporting Claude Code Sessions", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                val progress = TransferProgress { fraction, text ->
                    indicator.checkCanceled()
                    indicator.fraction = fraction
                    indicator.text2 = text
                }
                val outcome = SessionArchive.export(selected, destination, progress = progress)
                val details = buildString {
                    append("${outcome.exportedCount} session(s) exported to ${destination.fileName}.")
                    outcome.warnings.forEach { append("\n⚠ $it") }
                }
                val action = ClaudeNotifications.revealAction("Show in Explorer", destination)
                if (outcome.warnings.isEmpty()) {
                    ClaudeNotifications.info(project, "Export completed", details, action)
                } else {
                    ClaudeNotifications.warn(project, "Export completed with warnings", details, action)
                }
            }

            override fun onThrowable(error: Throwable) {
                ClaudeNotifications.error(project, "Export failed", error.message ?: error.javaClass.simpleName)
            }
        }.queue()
    }
}
