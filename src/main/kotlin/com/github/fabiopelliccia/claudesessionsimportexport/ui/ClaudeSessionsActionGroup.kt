package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

/**
 * The **Tools | Claude Code sessions** submenu.
 *
 * It exists only to give the group a translated title. `plugin.xml` can carry one, but that text
 * would be resolved through the IDE bundle mechanism, which follows an installed language pack and
 * nothing else, while every other string of this plugin follows the language of the machine as
 * well - see [ClaudeSessionsBundle]. Setting the presentation in [update] keeps the whole plugin on
 * one language instead of two.
 *
 * Both entries only read and write files and are safe while the IDE is indexing, hence [DumbAware].
 */
class ClaudeSessionsActionGroup : DefaultActionGroup(), DumbAware {

    companion object {
        // The submenu is rendered before either action is loaded: its title is the first text of
        // this plugin the user sees, so the IDE language has to be known by then.
        init {
            IdeDisplayLanguage.install()
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = ClaudeSessionsBundle.message("action.group.text")
    }
}
