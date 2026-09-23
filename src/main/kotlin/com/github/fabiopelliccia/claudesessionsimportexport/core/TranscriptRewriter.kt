package com.github.fabiopelliccia.claudesessionsimportexport.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Duration

/**
 * Adapts a transcript recorded on another machine to this one, line by line and key by key.
 *
 * Only the machine facing fields listed in the companion object are ever rewritten, and only at
 * the structural positions Claude Code writes them at: the top level of a line, the `snapshot` of a
 * `file-history-snapshot` line (with its `trackedFileBackups` map, keyed by absolute file path) and
 * the `backup` of a `file-history-delta` line. Nothing below `message`, `toolUseResult`,
 * `attachment` or any other key is ever looked at: that is what the user and Claude said to each
 * other, and file paths mentioned there belong to the conversation, not to this machine.
 *
 * A line none of those fields changes on is kept verbatim, and so are the line endings - including
 * the trailing newline Claude Code relies on when it appends to the transcript of a resumed session.
 */
class TranscriptRewriter(
    private val originalId: String,
    private val rewriteToId: String?,
    private val mapper: PathMapper?,
    private val delta: Duration,
) {

    companion object {
        /** Keys holding the session id: `session_id` is the spelling of `attachment` lines. */
        val ID_KEYS = setOf("sessionId", "session_id")

        /** Keys holding a working directory, see [PathMapper.mapCwd]. */
        val CWD_KEYS = setOf("cwd")

        /** Keys holding the absolute path of a file or of its folder, see [PathMapper.mapFile]. */
        val FILE_PATH_KEYS = setOf("realParentDir", "trackingPath")

        /** Keys holding an ISO-8601 instant. */
        val ISO_TIMESTAMP_KEYS = setOf("timestamp", "backupTime")

        /** Keys holding an epoch in milliseconds (the `cost-state` line). */
        val EPOCH_MILLIS_KEYS = setOf("startTime")

        private const val SNAPSHOT = "snapshot"
        private const val TRACKED_FILE_BACKUPS = "trackedFileBackups"
        private const val BACKUP = "backup"

        // JSONL is one compact object per line. Gson's defaults would also drop `null` members
        // (`"parentUuid":null`) and turn `<`, `>`, `=`, `'`, `&` into `<` style escapes, so
        // both are switched off to re-emit the line as Claude Code wrote it.
        private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    }

    /** What a rewrite did, for the import log: counts only, never a value of the transcript. */
    class Stats {
        var lines = 0
        var rewrittenLines = 0
        var unparsableLines = 0
        val changes: MutableMap<String, Int> = sortedMapOf()

        fun count(field: String) {
            changes[field] = (changes[field] ?: 0) + 1
        }
    }

    /** True when nothing would change, so the transcript can be copied byte for byte. */
    val isIdentity: Boolean
        get() = rewriteToId == null && mapper == null && delta.isZero

    fun rewrite(text: String, stats: Stats = Stats()): String {
        if (isIdentity) return text
        // Splitting on `\n` alone keeps a `\r` on its own line and turns the trailing newline into a
        // final empty element, so joining back reproduces the original layout exactly.
        return text.split('\n').joinToString("\n") { rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) return@joinToString rawLine
            stats.lines++
            val obj = runCatching { JsonParser.parseString(line) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject
            if (obj == null) {
                stats.unparsableLines++
                return@joinToString rawLine
            }
            if (!rewriteLine(obj, stats)) return@joinToString rawLine
            stats.rewrittenLines++
            gson.toJson(obj) + rawLine.substring(line.length)
        }
    }

    /** Returns whether any field actually changed, so that an untouched line can be kept verbatim. */
    private fun rewriteLine(obj: JsonObject, stats: Stats): Boolean {
        var changed = rewriteFields(obj, "", stats)

        obj.get(SNAPSHOT)?.takeIf { it.isJsonObject }?.asJsonObject?.let { snapshot ->
            changed = rewriteFields(snapshot, "$SNAPSHOT.", stats) || changed
            snapshot.get(TRACKED_FILE_BACKUPS)?.takeIf { it.isJsonObject }?.asJsonObject?.let { backups ->
                changed = rewriteTrackedFiles(snapshot, backups, stats) || changed
            }
        }
        obj.get(BACKUP)?.takeIf { it.isJsonObject }?.asJsonObject?.let { backup ->
            changed = rewriteFields(backup, "$BACKUP.", stats) || changed
        }
        return changed
    }

    private fun rewriteFields(obj: JsonObject, prefix: String, stats: Stats): Boolean {
        var changed = false
        for (key in ID_KEYS) {
            changed = rewriteString(obj, key, prefix, stats) { text ->
                if (rewriteToId != null && text == originalId) rewriteToId else null
            } || changed
        }
        for (key in CWD_KEYS) {
            changed = rewriteString(obj, key, prefix, stats) { text -> mapper?.mapCwd(text) } || changed
        }
        for (key in FILE_PATH_KEYS) {
            changed = rewriteString(obj, key, prefix, stats) { text -> mapper?.mapFile(text) } || changed
        }
        for (key in ISO_TIMESTAMP_KEYS) {
            changed = rewriteString(obj, key, prefix, stats) { text -> TimestampShift.shift(text, delta) } || changed
        }
        for (key in EPOCH_MILLIS_KEYS) {
            val element = obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber } ?: continue
            val value = runCatching { element.asLong }.getOrNull() ?: continue
            val shifted = TimestampShift.shiftEpochMillis(value, delta) ?: continue
            obj.addProperty(key, shifted)
            stats.count(prefix + key)
            changed = true
        }
        return changed
    }

    /**
     * `trackedFileBackups` is keyed by the absolute path of each tracked file: the keys are renamed
     * in place, keeping their order, and every value gets the same treatment as any other object.
     */
    private fun rewriteTrackedFiles(snapshot: JsonObject, backups: JsonObject, stats: Stats): Boolean {
        var changed = false
        val rebuilt = JsonObject()
        for ((path, value) in backups.entrySet()) {
            val mapped = mapper?.mapFile(path)?.takeIf { it != path }
            if (mapped != null && !backups.has(mapped)) {
                stats.count("$SNAPSHOT.$TRACKED_FILE_BACKUPS.<path>")
                changed = true
            }
            if (value.isJsonObject) {
                changed = rewriteFields(value.asJsonObject, "$SNAPSHOT.$TRACKED_FILE_BACKUPS.*.", stats) || changed
            }
            rebuilt.add(mapped?.takeIf { !backups.has(it) } ?: path, value)
        }
        if (changed) snapshot.add(TRACKED_FILE_BACKUPS, rebuilt)
        return changed
    }

    private fun rewriteString(
        obj: JsonObject,
        key: String,
        prefix: String,
        stats: Stats,
        transform: (String) -> String?,
    ): Boolean {
        val element = obj.get(key) ?: return false
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return false
        val current = element.asString
        val replacement = transform(current)?.takeIf { it != current } ?: return false
        obj.addProperty(key, replacement)
        stats.count(prefix + key)
        return true
    }
}
