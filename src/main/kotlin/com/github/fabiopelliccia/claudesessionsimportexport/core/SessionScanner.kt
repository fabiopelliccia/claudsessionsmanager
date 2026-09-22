package com.github.fabiopelliccia.claudesessionsimportexport.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

/**
 * Reads the local Claude Code `projects/` tree and turns each `<sessionId>.jsonl` transcript into
 * a [SessionInfo], without ever holding a full transcript in memory - lines are streamed once.
 */
object SessionScanner {

    private const val PREVIEW_MAX_LENGTH = 140

    fun listSessions(home: Path = ClaudePaths.resolveHome()): List<SessionInfo> {
        val projectsDir = ClaudePaths.projectsDir(home)
        if (!projectsDir.isDirectory()) return emptyList()

        val sessions = mutableListOf<SessionInfo>()
        Files.list(projectsDir).use { projectDirs ->
            projectDirs.asSequence()
                .filter { it.isDirectory() }
                .forEach { projectDir ->
                    Files.list(projectDir).use { entries ->
                        entries.asSequence()
                            .filter { it.isRegularFile() && it.extension == "jsonl" }
                            .forEach { transcript ->
                                sessions += readSession(projectDir, transcript)
                            }
                    }
                }
        }
        return sessions.sortedByDescending { it.lastTimestamp ?: "" }
    }

    private fun readSession(projectDir: Path, transcript: Path): SessionInfo {
        val id = transcript.nameWithoutExtension
        var firstTimestamp: String? = null
        var lastTimestamp: String? = null
        var cwd: String? = null
        var summary: String? = null
        var preview: String? = null
        var messageCount = 0

        Files.newBufferedReader(transcript).use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val obj = runCatching { JsonParser.parseString(line) }.getOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach

                obj.stringOrNull("timestamp")?.let { ts ->
                    if (firstTimestamp == null) firstTimestamp = ts
                    lastTimestamp = ts
                }
                if (cwd == null) cwd = obj.stringOrNull("cwd")

                when (obj.stringOrNull("type")) {
                    "summary" -> summary = obj.stringOrNull("summary") ?: summary
                    "user", "assistant" -> {
                        messageCount++
                        if (preview == null && obj.stringOrNull("type") == "user" &&
                            obj.get("isMeta")?.asBoolean != true
                        ) {
                            preview = extractPreview(obj)
                        }
                    }
                }
            }
        }

        val auxDir = projectDir.resolve(id)
        return SessionInfo(
            id = id,
            projectFolderName = projectDir.name,
            projectPath = cwd,
            firstTimestamp = firstTimestamp,
            lastTimestamp = lastTimestamp,
            messageCount = messageCount,
            sizeBytes = runCatching { Files.size(transcript) }.getOrDefault(0L),
            summary = summary ?: preview,
            hasAuxData = auxDir.isDirectory(),
        )
    }

    private fun extractPreview(userLine: JsonObject): String? {
        val message = userLine.getAsJsonObject("message") ?: return null
        val content = message.get("content") ?: return null
        val text = when {
            content.isJsonPrimitive -> content.asString
            content.isJsonArray -> content.asJsonArray.asSequence()
                .filter { it.isJsonObject && it.asJsonObject.stringOrNull("type") == "text" }
                .mapNotNull { it.asJsonObject.stringOrNull("text") }
                .firstOrNull()
            else -> null
        } ?: return null
        val singleLine = text.replace(Regex("\\s+"), " ").trim()
        if (singleLine.isEmpty()) return null
        return if (singleLine.length > PREVIEW_MAX_LENGTH) {
            singleLine.take(PREVIEW_MAX_LENGTH) + "…"
        } else {
            singleLine
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element: JsonElement = this.get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }
}
