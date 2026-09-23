package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudePaths
import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.github.fabiopelliccia.claudesessionsimportexport.core.PluginNames
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Shared plumbing for the export and import actions.
 *
 * [textKey] and [descriptionKey] name the bundle entries the presentation is built from: the texts
 * declared in `plugin.xml` are only the English fallback shown before this class is loaded.
 */
abstract class ClaudeSessionActionBase(
    private val textKey: String,
    private val descriptionKey: String,
) : DumbAwareAction() {

    companion object {
        init {
            IdeDisplayLanguage.install()
        }

        /** Title of the dialogs and message boxes; the product name is never translated. */
        internal const val TITLE = PluginNames.DISPLAY_NAME

        private val LOG = Logger.getInstance(ClaudeSessionActionBase::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = ClaudeSessionsBundle.message(textKey)
        e.presentation.description = ClaudeSessionsBundle.message(descriptionKey)
        e.presentation.putClientProperty(ActionUtil.SHOW_ICON_IN_MAIN_MENU, true)
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val home = ClaudePaths.resolveHome()
        if (requiresExistingHome && !home.isDirectory()) {
            Messages.showErrorDialog(
                project,
                ClaudeSessionsBundle.message("dialog.error.noClaudeData", home.toAbsolutePath()),
                TITLE,
            )
            return
        }
        perform(project, home)
    }

    /**
     * Export has nothing to read without a Claude Code home; import creates what it needs, so a
     * machine where Claude Code never ran can still receive sessions.
     */
    protected open val requiresExistingHome: Boolean = true

    protected abstract fun perform(project: Project?, home: Path)

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
