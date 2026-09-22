package com.github.fabiopelliccia.claudesessionsimportexport.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/**
 * Reads and writes the plugin's portable session archive: a plain ZIP with a JSON manifest plus,
 * for every session, its raw `transcript.jsonl` and a verbatim copy of the auxiliary folder Claude
 * Code keeps next to it (if any). On import, only three fields are ever rewritten - `sessionId`
 * (when a session is duplicated), `cwd` (when attached to a different local folder) and `timestamp`
 * (shifted to look recent) - see [import]. Everything else, in particular the conversation itself
 * under `message`, is copied completely untouched.
 */
object SessionArchive {

    private const val FORMAT_VERSION = 1
    private const val PRODUCER = "IntelliJ Claude Code sessions"
    private const val MANIFEST_ENTRY = "manifest.json"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // JSONL is one compact object per line: the manifest uses the pretty-printed `gson` above, but
    // rewriting a transcript line must never reformat it onto several lines.
    private val compactGson = Gson()

    private data class ArchiveManifest(
        val formatVersion: Int,
        val exportedAt: String,
        val producer: String,
        val sessions: List<SessionInfo>,
    )

    fun export(
        sessions: List<SessionInfo>,
        destination: Path,
        home: Path = ClaudePaths.resolveHome(),
        progress: TransferProgress = TransferProgress.NOOP,
    ): ExportOutcome {
        val projectsDir = ClaudePaths.projectsDir(home)
        val warnings = mutableListOf<String>()
        var exported = 0

        Files.createDirectories(destination.parent ?: destination.toAbsolutePath().parent)
        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(destination))).use { zip ->
            val manifest = ArchiveManifest(
                formatVersion = FORMAT_VERSION,
                exportedAt = Instant.now().toString(),
                producer = PRODUCER,
                sessions = sessions,
            )
            writeEntry(zip, MANIFEST_ENTRY, gson.toJson(manifest))

            sessions.forEachIndexed { index, session ->
                progress.report(index.toDouble() / sessions.size, session.displayName)

                val projectDir = projectsDir.resolve(session.projectFolderName)
                val transcript = projectDir.resolve("${session.id}.jsonl")
                if (!transcript.isRegularFile()) {
                    warnings += "Session ${session.id}: transcript not found, skipped"
                    return@forEachIndexed
                }

                zip.putNextEntry(ZipEntry("sessions/${session.id}/transcript.jsonl"))
                Files.copy(transcript, zip)
                zip.closeEntry()

                val auxDir = projectDir.resolve(session.id)
                if (auxDir.isDirectory()) {
                    copyDirectoryToZip(auxDir, "sessions/${session.id}/aux", zip)
                }
                exported++
            }
            progress.report(1.0, "")
        }
        return ExportOutcome(exportedCount = exported, warnings = warnings)
    }

    fun readManifest(archive: Path): List<SessionInfo> {
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry(MANIFEST_ENTRY)
                ?: throw IOException("Not a Claude Code sessions archive: missing $MANIFEST_ENTRY")
            val json = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).readText()
            val type = object : TypeToken<ArchiveManifest>() {}.type
            val manifest: ArchiveManifest = gson.fromJson(json, type)
            if (manifest.formatVersion > FORMAT_VERSION) {
                throw IOException(
                    "Archive format version ${manifest.formatVersion} is newer than this plugin " +
                        "supports (max $FORMAT_VERSION). Please update the plugin."
                )
            }
            return manifest.sessions
        }
    }

    /**
     * @param targetProjectPath Local folder the imported sessions should be attached to. Needed
     * whenever the archive crosses machines (or users): the `cwd` and project folder name recorded
     * in the archive belong to the source machine and are otherwise meaningless here. Leaving it
     * `null` falls back to recomputing the folder from the session's own recorded path, useful only
     * when restoring straight back onto the same machine/user.
     * @param shiftTimestampsToNow Moves every timestamp of a session by the same delta so its last
     * message lands at "now" - the imported session then shows up as recent, like one just had.
     */
    fun import(
        archive: Path,
        sessionIds: Set<String>? = null,
        conflictPolicy: ConflictPolicy = ConflictPolicy.DUPLICATE,
        targetProjectPath: String? = null,
        shiftTimestampsToNow: Boolean = true,
        home: Path = ClaudePaths.resolveHome(),
        progress: TransferProgress = TransferProgress.NOOP,
    ): ImportOutcome {
        val manifestSessions = readManifest(archive).filter { sessionIds == null || it.id in sessionIds }
        val projectsDir = ClaudePaths.projectsDir(home)
        val imported = mutableListOf<ImportedSession>()
        val skipped = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        ZipFile(archive.toFile()).use { zip ->
            manifestSessions.forEachIndexed { index, session ->
                progress.report(index.toDouble() / manifestSessions.size, session.displayName)

                val transcriptEntry = zip.getEntry("sessions/${session.id}/transcript.jsonl")
                if (transcriptEntry == null) {
                    warnings += "Session ${session.id}: transcript missing from archive, skipped"
                    return@forEachIndexed
                }

                val resolvedCwd = targetProjectPath ?: session.projectPath
                val folderName = resolvedCwd?.let { ClaudePaths.encodeProjectPath(it) } ?: session.projectFolderName
                val targetDir = projectsDir.resolve(folderName)
                Files.createDirectories(targetDir)

                val existingTranscript = targetDir.resolve("${session.id}.jsonl")
                val conflict = Files.exists(existingTranscript)

                if (conflict && conflictPolicy == ConflictPolicy.SKIP) {
                    skipped += session.id
                    return@forEachIndexed
                }

                val writtenId: String
                val action: String
                val rewriteId: String?
                if (conflict && conflictPolicy == ConflictPolicy.DUPLICATE) {
                    writtenId = UUID.randomUUID().toString()
                    action = "duplicated"
                    rewriteId = session.id
                } else {
                    writtenId = session.id
                    action = if (conflict) "overwritten" else "new"
                    rewriteId = null
                }

                val cwdRewrite = if (targetProjectPath != null && session.projectPath != null &&
                    targetProjectPath != session.projectPath
                ) {
                    session.projectPath to targetProjectPath
                } else {
                    null
                }
                val timestampDelta = if (shiftTimestampsToNow) {
                    TimestampShift.deltaToNow(session.lastTimestamp ?: session.firstTimestamp)
                } else {
                    Duration.ZERO
                }

                writeTranscript(
                    zip,
                    transcriptEntry,
                    targetDir.resolve("$writtenId.jsonl"),
                    originalId = session.id,
                    rewriteToId = rewriteId,
                    cwdRewrite = cwdRewrite,
                    timestampDelta = timestampDelta,
                )

                val auxPrefix = "sessions/${session.id}/aux/"
                val auxTargetDir = targetDir.resolve(writtenId)
                extractAuxFiles(zip, auxPrefix, auxTargetDir)

                imported += ImportedSession(
                    sourceId = session.id,
                    writtenId = writtenId,
                    projectFolderName = folderName,
                    action = action,
                )
            }
            progress.report(1.0, "")
        }
        return ImportOutcome(imported = imported, skipped = skipped, warnings = warnings)
    }

    /**
     * Copies the transcript line by line. When neither the id, the `cwd` nor any timestamp needs
     * touching, the entry is copied byte for byte; otherwise each line is parsed and only its
     * `sessionId`, `cwd` and `timestamp` fields (plus the nested `snapshot.timestamp` a
     * `file-history-snapshot` line carries) are rewritten - everything else, in particular anything
     * under `message`, is re-emitted completely untouched.
     */
    private fun writeTranscript(
        zip: ZipFile,
        entry: ZipEntry,
        target: Path,
        originalId: String,
        rewriteToId: String?,
        cwdRewrite: Pair<String, String>?,
        timestampDelta: Duration,
    ) {
        if (rewriteToId == null && cwdRewrite == null && timestampDelta.isZero) {
            zip.getInputStream(entry).use { input ->
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            return
        }

        val lines = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).readLines()
        val rewritten = lines.map { line ->
            if (line.isBlank()) return@map line
            val obj = runCatching { JsonParser.parseString(line) }.getOrNull()
                ?.takeIf { it.isJsonObject }?.asJsonObject ?: return@map line
            rewriteLine(obj, originalId, rewriteToId, cwdRewrite, timestampDelta)
            compactGson.toJson(obj)
        }
        Files.write(target, rewritten.joinToString("\n").toByteArray(StandardCharsets.UTF_8))
    }

    private fun rewriteLine(
        obj: JsonObject,
        originalId: String,
        rewriteToId: String?,
        cwdRewrite: Pair<String, String>?,
        timestampDelta: Duration,
    ) {
        rewriteStringField(obj, "sessionId") { text ->
            if (rewriteToId != null && text == originalId) rewriteToId else null
        }
        rewriteStringField(obj, "cwd") { text ->
            cwdRewrite?.takeIf { (from, _) -> text.startsWith(from, ignoreCase = true) }
                ?.let { (from, to) -> to + text.substring(from.length) }
        }
        rewriteStringField(obj, "timestamp") { text -> TimestampShift.shift(text, timestampDelta) }

        // The only known nested spot: a `file-history-snapshot` line carries a second copy of the
        // timestamp one level down, under `snapshot`.
        obj.get("snapshot")?.takeIf { it.isJsonObject }?.asJsonObject?.let { snapshot ->
            rewriteStringField(snapshot, "timestamp") { text -> TimestampShift.shift(text, timestampDelta) }
        }
    }

    private fun rewriteStringField(obj: JsonObject, key: String, transform: (String) -> String?) {
        val element = obj.get(key) ?: return
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return
        transform(element.asString)?.let { obj.addProperty(key, it) }
    }

    private fun extractAuxFiles(zip: ZipFile, prefix: String, targetDir: Path) {
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
            val relative = entry.name.removePrefix(prefix)
            val target = resolveSafely(targetDir, relative) ?: continue
            Files.createDirectories(target.parent)
            zip.getInputStream(entry).use { input ->
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Guards against zip-slip: refuses any entry whose resolved path escapes [base]. */
    private fun resolveSafely(base: Path, relative: String): Path? {
        val resolved = base.resolve(relative).normalize()
        return if (resolved.startsWith(base.normalize())) resolved else null
    }

    private fun copyDirectoryToZip(dir: Path, entryPrefix: String, zip: ZipOutputStream) {
        Files.walk(dir).use { paths ->
            paths.asSequence()
                .filter { it.isRegularFile() }
                .forEach { file ->
                    val relative = dir.relativize(file).toString().replace('\\', '/')
                    zip.putNextEntry(ZipEntry("$entryPrefix/$relative"))
                    Files.copy(file, zip)
                    zip.closeEntry()
                }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
