package com.github.fabiopelliccia.claudesessionsimportexport.core

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the local Claude Code home directory and the layout Claude Code uses underneath it.
 *
 * Claude Code keeps every session transcript as a JSONL file at
 * `<home>/projects/<encodedProjectPath>/<sessionId>.jsonl`, with an optional sibling directory
 * `<home>/projects/<encodedProjectPath>/<sessionId>/` holding auxiliary data referenced from the
 * transcript (subagent runs, tool results, ...). Both are treated as one unit by this plugin.
 */
object ClaudePaths {

    /** Same override Claude Code itself honors, so a relocated home is picked up automatically. */
    const val ENV_VAR = "CLAUDE_CONFIG_DIR"

    fun resolveHome(): Path {
        val override = System.getProperty("claude.home") ?: System.getenv(ENV_VAR)
        if (!override.isNullOrBlank()) {
            return Paths.get(override)
        }
        return Paths.get(System.getProperty("user.home"), ".claude")
    }

    fun projectsDir(home: Path = resolveHome()): Path = home.resolve("projects")

    /**
     * Best-effort replication of the folder-naming scheme Claude Code applies to a project's
     * working directory: every character that isn't a letter or digit becomes `-`. This is only
     * needed when importing a session for a project that has no local Claude Code folder yet;
     * an existing folder found by [SessionScanner] is always preferred over a freshly computed one.
     */
    fun encodeProjectPath(projectPath: String): String =
        projectPath.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
}
