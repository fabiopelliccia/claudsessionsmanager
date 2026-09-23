package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.github.fabiopelliccia.claudesessionsimportexport.core.PluginNames
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection
import java.nio.file.Path

internal object ClaudeNotifications {

    /** Must match the `<notificationGroup id>` of `plugin.xml`, or no notification is ever shown. */
    const val GROUP_ID = PluginNames.DISPLAY_NAME

    fun info(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.INFORMATION, actions)

    fun warn(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.WARNING, actions)

    fun error(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.ERROR, actions)

    /** Opens the folder of the written archive with the file selected. */
    fun showArchiveAction(archive: Path): NotificationAction =
        NotificationAction.createSimpleExpiring(ClaudeSessionsBundle.message("notification.action.showArchive")) {
            RevealFileAction.openFile(archive)
        }

    /**
     * Opens the folder of the diagnostic import log with the file selected. Shown on every import
     * notification - informative, warning **and** error - because the log is written unconditionally
     * and is most useful exactly when the import did not go as expected.
     */
    fun showLogAction(logFile: Path): NotificationAction =
        NotificationAction.createSimpleExpiring(ClaudeSessionsBundle.message("notification.action.showLog")) {
            RevealFileAction.openFile(logFile)
        }

    /**
     * Claude Code reads its session list from disk every time `/resume` or `claude --resume` runs,
     * so an imported session needs no IDE restart - only the command to continue it, which is what
     * this action puts on the clipboard.
     */
    fun copyResumeAction(command: String): NotificationAction =
        NotificationAction.create(ClaudeSessionsBundle.message("notification.action.copyResume")) { _, _ ->
            CopyPasteManager.getInstance().setContents(StringSelection(command))
        }

    private fun notify(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType,
        actions: Array<out NotificationAction>,
    ) {
        val notification: Notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
        actions.forEach { notification.addAction(it) }
        notification.notify(project)
    }
}
