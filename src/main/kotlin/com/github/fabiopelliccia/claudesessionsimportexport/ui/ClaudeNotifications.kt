package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.nio.file.Path

internal object ClaudeNotifications {

    private const val GROUP_ID = "Claude Code sessions"

    fun info(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.INFORMATION, actions)

    fun warn(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.WARNING, actions)

    fun error(project: Project?, title: String, content: String, vararg actions: NotificationAction) =
        notify(project, title, content, NotificationType.ERROR, actions)

    fun revealAction(text: String, file: Path): NotificationAction =
        NotificationAction.createSimpleExpiring(text) {
            RevealFileAction.openFile(file)
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
