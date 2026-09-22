package com.github.fabiopelliccia.claudesessionsimportexport.core

/**
 * Everything the plugin knows about one local Claude Code session without parsing the full
 * transcript - enough to list it, sort it and decide where to write it back on import.
 */
data class SessionInfo(
    val id: String,
    /** Folder name under `<home>/projects/`, e.g. `C--Users-pelliccia-IdeaProjects-Foo`. */
    val projectFolderName: String,
    /** Original working directory the session was recorded from, read back from the transcript. */
    val projectPath: String?,
    val firstTimestamp: String?,
    val lastTimestamp: String?,
    val messageCount: Int,
    val sizeBytes: Long,
    /** Short human-readable label: the `summary` line if Claude Code wrote one, else a preview of the first user message. */
    val summary: String?,
    /** Whether a sibling `<sessionId>/` auxiliary folder exists next to the transcript. */
    val hasAuxData: Boolean,
) {
    val displayName: String
        get() = summary?.takeIf { it.isNotBlank() } ?: id
}

enum class ConflictPolicy {
    SKIP,
    OVERWRITE,
    DUPLICATE,
}

fun interface TransferProgress {
    fun report(fraction: Double, text: String)

    companion object {
        val NOOP = TransferProgress { _, _ -> }
    }
}

data class ImportedSession(
    val sourceId: String,
    val writtenId: String,
    val projectFolderName: String,
    /** What actually happened to this session: "new", "overwritten" or "duplicated". */
    val action: String,
)

data class ImportOutcome(
    val imported: List<ImportedSession>,
    val skipped: List<String>,
    val warnings: List<String>,
)

data class ExportOutcome(
    val exportedCount: Int,
    val warnings: List<String>,
)
