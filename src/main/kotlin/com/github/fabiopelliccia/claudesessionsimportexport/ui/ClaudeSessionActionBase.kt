package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudePaths
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlin.io.path.isDirectory

/** Shared plumbing for the export and import actions. */
abstract class ClaudeSessionActionBase : DumbAwareAction() {

    companion object {
        internal const val TITLE = "Claude Code sessions"

        private val LOG = Logger.getInstance(ClaudeSessionActionBase::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val home = ClaudePaths.resolveHome()
        if (!home.isDirectory()) {
            Messages.showErrorDialog(
                project,
                "No Claude Code data found at ${home.toAbsolutePath()}.",
                TITLE,
            )
            return
        }
        perform(project)
    }

    protected abstract fun perform(project: Project?)

    /**
     * Runs a short blocking operation (a directory scan or archive read) with a modal progress, so
     * the EDT never touches the file system itself.
     *
     * A cancellation is re-thrown rather than reported: it is control flow, not a failure, and
     * swallowing it would both show the user an error they caused on purpose and break the
     * platform's own cancellation handling.
     */
    protected fun <T> runWithProgress(project: Project?, title: String, action: () -> T): T? =
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<T, Exception>(
                { action() },
                title,
                false,
                project,
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("$title failed", e)
            Messages.showErrorDialog(project, e.message ?: e.javaClass.simpleName, TITLE)
            null
        }
}
