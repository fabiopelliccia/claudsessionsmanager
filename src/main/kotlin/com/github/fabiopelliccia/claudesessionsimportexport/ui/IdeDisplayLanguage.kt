package com.github.fabiopelliccia.claudesessionsimportexport.ui

import com.github.fabiopelliccia.claudesessionsimportexport.core.ClaudeSessionsBundle
import com.intellij.DynamicBundle

/**
 * Hands the display language of the IDE to [ClaudeSessionsBundle].
 *
 * The bundle lives in `core/`, which may not touch the IntelliJ API, so the only piece that needs
 * it - reading the language of an installed language pack - is supplied from here. [install] is
 * called while [ClaudeSessionsActionGroup] or [ClaudeSessionActionBase] is loaded, that is before
 * any text of this plugin can be requested: every user visible message is produced by the menu or
 * by one of its two actions.
 */
internal object IdeDisplayLanguage {

    fun install() {
        ClaudeSessionsBundle.displayLanguage = { runCatching { DynamicBundle.getLocale() }.getOrNull() }
    }
}
