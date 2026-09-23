package com.github.fabiopelliccia.claudesessionsimportexport.core

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Reads and writes the plugin's portable session archive: a plain ZIP with a JSON manifest plus,
 * for every session, its `transcript.jsonl`, a verbatim copy of the auxiliary folder Claude Code
 * keeps next to it and of its file history (the backups behind checkpoints and `/rewind`), when
 * present:
 *
 * ```
 * manifest.json
 * sessions/<id>/transcript.jsonl
 * sessions/<id>/aux/...
 * sessions/<id>/file-history/...
 * ```
 *
 * The archive is self-describing: an entry that is missing is handled by its absence, never by the
 * format number, which only exists to refuse a layout this build cannot understand. On import only
 * the machine facing fields of the transcript are rewritten - see [TranscriptRewriter]; everything
 * else, in particular the conversation itself, is copied completely untouched.
 */
object SessionArchive {

    const val ARCHIVE_EXTENSION = "zip"
    const val FORMAT_VERSION = 1
    const val PRODUCER = PluginNames.DISPLAY_NAME
    private const val MANIFEST_ENTRY = "manifest.json"

    private const val ACTION_NEW = "new"
    private const val ACTION_REPLACED = "replaced"
    private const val ACTION_DUPLICATED = "duplicated"

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    data class ArchiveManifest(
        val formatVersion: Int,
        val exportedAt: String,
        val producer: String,
        val sessions: List<SessionInfo>,
        /** Claude Code home of the source machine; informative only, written to the import log. */
        val sourceHome: String? = null,
    )

    private fun transcriptEntry(id: String) = "sessions/$id/transcript.jsonl"
    private fun auxPrefix(id: String) = "sessions/$id/aux/"
    private fun fileHistoryPrefix(id: String) = "sessions/$id/file-history/"

    // ------------------------------------------------------------------------------- export --

    fun export(
        sessions: List<SessionInfo>,
        destination: Path,
        home: Path = ClaudePaths.resolveHome(),
        progress: TransferProgress = TransferProgress.NOOP,
    ): ExportOutcome {
        val projectsDir = ClaudePaths.projectsDir(home)
        val warnings = mutableListOf<String>()
        val exported = mutableListOf<SessionInfo>()
        var unreadable = 0

        Files.createDirectories(destination.toAbsolutePath().parent)
        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(destination))).use { zip ->
            sessions.forEachIndexed { index, session ->
                progress.report(
                    index.toDouble() / sessions.size,
                    ClaudeSessionsBundle.message("progress.exportingSession", session.displayName),
                )

                val projectDir = projectsDir.resolve(session.projectFolderName)
                val transcript = projectDir.resolve("${session.id}.jsonl")
                if (!transcript.isRegularFile()) {
                    warnings += ClaudeSessionsBundle.message("export.warning.noTranscript", session.displayName)
                    return@forEachIndexed
                }
                // Read before the entry is opened: a file that fails half way must not leave a
                // truncated transcript in the archive.
                val bytes = try {
                    Files.readAllBytes(transcript)
                } catch (e: IOException) {
                    unreadable++
                    warnings += ClaudeSessionsBundle.message("export.warning.unreadable", session.displayName, transcript)
                    return@forEachIndexed
                }
                writeEntry(zip, transcriptEntry(session.id), bytes)

                val auxDir = projectDir.resolve(session.id)
                if (auxDir.isDirectory()) {
                    unreadable += copyDirectoryToZip(auxDir, auxPrefix(session.id), zip) { file ->
                        warnings += ClaudeSessionsBundle.message("export.warning.unreadable", session.displayName, file)
                    }
                }
                val fileHistoryDir = ClaudePaths.fileHistoryDir(home).resolve(session.id)
                if (fileHistoryDir.isDirectory()) {
                    unreadable += copyDirectoryToZip(fileHistoryDir, fileHistoryPrefix(session.id), zip) { file ->
                        warnings += ClaudeSessionsBundle.message("export.warning.unreadable", session.displayName, file)
                    }
                }
                exported += session.copy(hasAuxData = auxDir.isDirectory(), hasFileHistory = fileHistoryDir.isDirectory())
            }

            // Written last, so it lists exactly the sessions that made it into the archive.
            val manifest = ArchiveManifest(
                formatVersion = FORMAT_VERSION,
                exportedAt = Instant.now().toString(),
                producer = PRODUCER,
                sessions = exported,
                sourceHome = home.toAbsolutePath().toString(),
            )
            writeEntry(zip, MANIFEST_ENTRY, gson.toJson(manifest).toByteArray(StandardCharsets.UTF_8))
            progress.report(1.0, ClaudeSessionsBundle.message("progress.exportCompleted"))
        }
        return ExportOutcome(exportedCount = exported.size, warnings = warnings, unreadableFiles = unreadable)
    }

    // ----------------------------------------------------------------------------- manifest --

    fun readManifest(archive: Path): List<SessionInfo> = readArchiveManifest(archive).sessions

    fun readArchiveManifest(archive: Path): ArchiveManifest {
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry(MANIFEST_ENTRY)
                ?: throw IOException(ClaudeSessionsBundle.message("error.notAnArchive"))
            val json = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).readText()
            val type = object : TypeToken<ArchiveManifest>() {}.type
            val manifest: ArchiveManifest = runCatching { gson.fromJson<ArchiveManifest>(json, type) }.getOrNull()
                ?: throw IOException(ClaudeSessionsBundle.message("error.unreadableManifest"))
            if (manifest.formatVersion > FORMAT_VERSION) {
                throw IOException(ClaudeSessionsBundle.message("error.archiveTooNew", manifest.formatVersion, FORMAT_VERSION))
            }
            // Gson fills a field missing from the JSON with null even when Kotlin declares it
            // non-null: an archive without a session list is read as an empty one.
            @Suppress("SENSELESS_COMPARISON")
            return if (manifest.sessions == null) manifest.copy(sessions = emptyList()) else manifest
        }
    }

    // ------------------------------------------------------------------------------- import --

    /**
     * Restores the sessions of [archive] (all of them, or only [sessionIds]).
     *
     * [targetProjectPath] attaches the sessions to a folder of this machine: the recorded `cwd`
     * and project folder name come from the source machine (or user) and are meaningless anywhere
     * else. When it is `null` each session keeps the folder it was recorded in. [conflictPolicy]
     * decides what happens to a session whose id already exists anywhere under `projects/`.
     *
     * With [shiftTimestampsToNow] every timestamp of a session moves by one delta, so its last
     * message lands at import time while the spacing between messages is preserved. Every step is
     * written to [log]; a session that fails is reported in [ImportOutcome.failures] and does not
     * stop the others.
     */
    fun import(
        archive: Path,
        sessionIds: Set<String>? = null,
        conflictPolicy: ConflictPolicy = ConflictPolicy.DUPLICATE,
        targetProjectPath: String? = null,
        shiftTimestampsToNow: Boolean = true,
        home: Path = ClaudePaths.resolveHome(),
        progress: TransferProgress = TransferProgress.NOOP,
        log: ImportLog = ImportLog.NOOP,
        environment: ImportEnvironment = ImportEnvironment(),
    ): ImportOutcome {
        val startedAt = System.currentTimeMillis()
        val importedAt = Instant.now()
        val attachedFolder = targetProjectPath?.takeIf { it.isNotBlank() }?.let(ClaudePaths::normalizeProjectPath)
        logEnvironment(log, environment, home)
        logOperationContext(log, archive, conflictPolicy, targetProjectPath, attachedFolder, shiftTimestampsToNow)

        val manifest = readArchiveManifest(archive)
        val selected = manifest.sessions.filter { sessionIds == null || it.id in sessionIds }
        logManifest(log, manifest, selected)

        val existing = SessionScanner.existingTranscripts(home).toMutableMap()
        val nativeTranscript = attachedFolder?.let { latestTranscript(ClaudePaths.projectsDir(home).resolve(ClaudePaths.encodeProjectPath(it))) }

        val imported = mutableListOf<ImportedSession>()
        val targets = mutableListOf<ImportDiagnostics.Target>()
        val skipped = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val failures = mutableListOf<ImportFailure>()

        log.section(ClaudeSessionsBundle.message("log.section.sessions"))
        ZipFile(archive.toFile()).use { zip ->
            selected.forEachIndexed { index, session ->
                progress.report(
                    index.toDouble() / selected.size,
                    ClaudeSessionsBundle.message("progress.importingSession", session.displayName),
                )
                val sessionLog = log.child("Session ${session.id}")
                try {
                    val context = SingleImport(zip, session, conflictPolicy, attachedFolder, shiftTimestampsToNow, importedAt, home, existing)
                    when (val result = importSingle(context, sessionLog, warnings)) {
                        null -> skipped += session.id
                        else -> {
                            imported += result
                            existing[result.writtenId] = listOf(
                                ClaudePaths.projectsDir(home).resolve(result.projectFolderName).resolve("${result.writtenId}.jsonl"),
                            )
                            targets += ImportDiagnostics.Target(result, session.projectPath, attachedFolder, shiftTimestampsToNow)
                        }
                    }
                } catch (e: Exception) {
                    sessionLog.failure("import of ${session.id} failed", e)
                    failures += ImportFailure(session.id, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        progress.report(1.0, ClaudeSessionsBundle.message("progress.importCompleted"))

        val failedChecks = runCatching { ImportDiagnostics(home).diagnose(targets, nativeTranscript, importedAt, log) }
            .onFailure { log.failure("diagnosis failed", it) }
            .getOrDefault(emptyList())
        logSummary(log, imported, skipped, failures, failedChecks, startedAt)
        return ImportOutcome(imported, skipped, warnings, failures, failedChecks)
    }

    private class SingleImport(
        val zip: ZipFile,
        val session: SessionInfo,
        val policy: ConflictPolicy,
        val attachedFolder: String?,
        val shift: Boolean,
        val importedAt: Instant,
        val home: Path,
        val existing: Map<String, List<Path>>,
    )

    /** Imports one session; returns `null` when it is skipped. */
    private fun importSingle(context: SingleImport, log: ImportLog, warnings: MutableList<String>): ImportedSession? {
        val session = context.session
        val zip = context.zip
        // No display name here: without a `summary` line it is a preview of the first user
        // message, that is conversation text, which the log never contains.
        log.kv("source.projectFolder", session.projectFolderName)
        log.kv("source.cwd", session.projectPath)
        log.kv("source.lastTimestamp", session.lastTimestamp)

        val transcriptEntry = zip.getEntry(transcriptEntry(session.id))
        if (transcriptEntry == null) {
            log.line("transcript missing from the archive: skipped")
            warnings += ClaudeSessionsBundle.message("import.warning.noTranscript", session.displayName)
            return null
        }

        val resolvedCwd = context.attachedFolder ?: session.projectPath
        val folderName = resolvedCwd?.let(ClaudePaths::encodeProjectPath) ?: session.projectFolderName
        val projectsDir = ClaudePaths.projectsDir(context.home)
        val targetDir = projectsDir.resolve(folderName)
        val existingCopies = context.existing[session.id].orEmpty()
        val conflict = existingCopies.isNotEmpty()
        log.kv("target.projectFolder", folderName)
        log.kv("conflict", conflict)
        if (conflict) log.kv("conflict.existingCopies", existingCopies.map { it.parent.fileName.toString() })

        if (conflict && context.policy == ConflictPolicy.SKIP) {
            log.line("already present: skipped (policy SKIP)")
            return null
        }

        val (writtenId, action) = when {
            conflict && context.policy == ConflictPolicy.DUPLICATE -> UUID.randomUUID().toString() to ACTION_DUPLICATED
            conflict -> session.id to ACTION_REPLACED
            else -> session.id to ACTION_NEW
        }
        log.kv("action", action)
        log.kv("writtenId", writtenId)

        if (action == ACTION_REPLACED) {
            // Replacing means the local session is gone afterwards, wherever it lived: leaving a
            // copy in another project folder would keep two sessions with the same id around.
            for (copy in existingCopies) {
                deleteSession(copy, context.home, log)
            }
        }

        val mapper = context.attachedFolder?.let { PathMapper(session.projectPath, it) }
        val delta = if (context.shift) {
            TimestampShift.deltaToNow(session.lastTimestamp ?: session.firstTimestamp, context.importedAt)
        } else {
            Duration.ZERO
        }
        log.kv("timestamps.delta", delta)
        log.kv("pathMapper.target", mapper?.target)

        Files.createDirectories(targetDir)
        val targetFile = targetDir.resolve("$writtenId.jsonl")
        val rewriter = TranscriptRewriter(session.id, writtenId.takeIf { it != session.id }, mapper, delta)
        val stats = TranscriptRewriter.Stats()
        if (rewriter.isIdentity) {
            zip.getInputStream(transcriptEntry).use { Files.copy(it, targetFile, StandardCopyOption.REPLACE_EXISTING) }
            log.line("transcript copied byte for byte")
        } else {
            val text = zip.getInputStream(transcriptEntry).bufferedReader(StandardCharsets.UTF_8).readText()
            Files.write(targetFile, rewriter.rewrite(text, stats).toByteArray(StandardCharsets.UTF_8))
            log.kv("transcript.lines", stats.lines)
            log.kv("transcript.rewrittenLines", stats.rewrittenLines)
            log.kv("transcript.unparsableLines", stats.unparsableLines)
            for ((field, count) in stats.changes) log.kv("transcript.changed.$field", count)
        }

        val aux = extractEntries(zip, auxPrefix(session.id), targetDir.resolve(writtenId))
        log.kv("aux.filesWritten", aux.written)
        val fileHistory = extractEntries(zip, fileHistoryPrefix(session.id), ClaudePaths.fileHistoryDir(context.home).resolve(writtenId))
        log.kv("fileHistory.filesWritten", fileHistory.written)
        val discarded = aux.discarded + fileHistory.discarded
        if (discarded.isNotEmpty()) log.kv("discardedZipSlipEntries", discarded)

        // Re-read from disk: the notification and the diagnosis describe what Claude Code will
        // actually find, not what this method meant to write.
        val written = SessionScanner.describe(targetFile, context.home)
        log.kv("reread.cwd", written.projectPath)
        log.kv("reread.messageCount", written.messageCount)
        log.kv("reread.lastTimestamp", written.lastTimestamp)
        log.kv("reread.sizeBytes", written.sizeBytes)
        return ImportedSession(
            sourceId = session.id,
            writtenId = writtenId,
            projectFolderName = folderName,
            action = action,
            displayName = written.displayName,
            cwd = written.projectPath,
            messageCount = written.messageCount,
            fileHistoryFiles = fileHistory.written,
        )
    }

    /** Removes a local session: its transcript, its auxiliary folder and its file history. */
    private fun deleteSession(transcript: Path, home: Path, log: ImportLog) {
        val id = transcript.fileName.toString().removeSuffix(".jsonl")
        Files.deleteIfExists(transcript)
        deleteRecursively(transcript.resolveSibling(id))
        deleteRecursively(ClaudePaths.fileHistoryDir(home).resolve(id))
        log.line("replaced: removed the local copy in ${transcript.parent.fileName}")
    }

    private fun deleteRecursively(dir: Path) {
        if (!dir.isDirectory()) return
        Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    /** The most recently modified transcript of [projectDir], or `null` when it holds none. */
    private fun latestTranscript(projectDir: Path): Path? = runCatching {
        if (!projectDir.isDirectory()) return null
        Files.list(projectDir).use { entries ->
            entries.filter { it.isRegularFile() && it.fileName.toString().endsWith(".jsonl") }
                .max(compareBy { Files.getLastModifiedTime(it) })
                .orElse(null)
        }
    }.getOrNull()

    private class Extraction(val written: Int, val discarded: List<String>)

    private fun extractEntries(zip: ZipFile, prefix: String, targetDir: Path): Extraction {
        var written = 0
        val discarded = mutableListOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
            val target = resolveSafely(targetDir, entry.name.removePrefix(prefix))
            if (target == null) {
                discarded += entry.name
                continue
            }
            Files.createDirectories(target.parent)
            zip.getInputStream(entry).use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
            written++
        }
        return Extraction(written, discarded)
    }

    /** Guards against zip-slip: refuses any entry whose resolved path escapes [base]. */
    private fun resolveSafely(base: Path, relative: String): Path? {
        val resolved = base.resolve(relative).normalize()
        return if (resolved.startsWith(base.normalize())) resolved else null
    }

    /**
     * Copies every file of [dir] below [entryPrefix]; a file that cannot be read is reported to
     * [onUnreadable] and left out instead of aborting the export. Returns how many were left out.
     */
    private fun copyDirectoryToZip(dir: Path, entryPrefix: String, zip: ZipOutputStream, onUnreadable: (Path) -> Unit): Int {
        var unreadable = 0
        Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
                val bytes = try {
                    Files.readAllBytes(file)
                } catch (e: IOException) {
                    unreadable++
                    onUnreadable(file)
                    return FileVisitResult.CONTINUE
                }
                writeEntry(zip, entryPrefix + dir.relativize(file).toString().replace('\\', '/'), bytes)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                unreadable++
                onUnreadable(file)
                return FileVisitResult.CONTINUE
            }
        })
        return unreadable
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content)
        zip.closeEntry()
    }

    // ---------------------------------------------------------------------------------- log --

    private fun logEnvironment(log: ImportLog, environment: ImportEnvironment, home: Path) {
        log.section(ClaudeSessionsBundle.message("log.section.environment"))
        log.kv("import.localTime", java.time.LocalDateTime.now())
        log.kv("import.utcTime", Instant.now())
        log.kv("import.timeZone", java.util.TimeZone.getDefault().id)
        log.kv("locale", Locale.getDefault())
        log.kv("ui.locale", ClaudeSessionsBundle.effectiveLocale().takeIf { it.language.isNotEmpty() } ?: "en (default)")
        log.kv("pluginVersion", environment.pluginVersion)
        log.kv("ideBuild", environment.ideBuild)
        log.kv("productName", environment.productName)
        log.kv("java.version", System.getProperty("java.version"))
        log.kv("os.name", System.getProperty("os.name"))
        log.kv("os.version", System.getProperty("os.version"))
        log.kv("os.arch", System.getProperty("os.arch"))
        log.kv("user.home", System.getProperty("user.home"))
        log.kv("file.separator", System.getProperty("file.separator"))
        log.kv("${ClaudePaths.SYSTEM_PROPERTY} (system property)", System.getProperty(ClaudePaths.SYSTEM_PROPERTY))
        log.kv("${ClaudePaths.ENV_VAR} (env)", System.getenv(ClaudePaths.ENV_VAR))
        log.kv("home (resolved)", home.toAbsolutePath())
        val projectsDir = ClaudePaths.projectsDir(home)
        log.kv("projectsDir.exists", projectsDir.isDirectory())
        log.kv("projectsDir.writable", Files.isWritable(projectsDir))
        log.kv("fileHistoryDir.exists", ClaudePaths.fileHistoryDir(home).isDirectory())
    }

    private fun logOperationContext(
        log: ImportLog,
        archive: Path,
        policy: ConflictPolicy,
        requestedFolder: String?,
        attachedFolder: String?,
        shift: Boolean,
    ) {
        log.section(ClaudeSessionsBundle.message("log.section.context"))
        log.kv("archive.path", archive.toAbsolutePath())
        log.kv("archive.sizeBytes", runCatching { Files.size(archive) }.getOrNull())
        log.kv("archive.modified", runCatching { Files.getLastModifiedTime(archive) }.getOrNull())
        log.kv("policy", policy.name)
        log.kv("attachTo.raw", requestedFolder)
        log.kv("attachTo.normalized", attachedFolder)
        log.kv("attachTo.projectFolder", attachedFolder?.let(ClaudePaths::encodeProjectPath))
        log.kv("shiftTimestampsToNow", shift)
    }

    private fun logManifest(log: ImportLog, manifest: ArchiveManifest, selected: List<SessionInfo>) {
        log.kv("manifest.formatVersion", manifest.formatVersion)
        log.kv("manifest.exportedAt", manifest.exportedAt)
        log.kv("manifest.producer", manifest.producer)
        log.kv("manifest.sourceHome", manifest.sourceHome)
        log.kv("manifest.sessionCount", manifest.sessions.size)
        for (session in manifest.sessions) {
            log.line(
                "manifest session ${session.id}: folder=${session.projectFolderName}, messages=${session.messageCount}, " +
                    "aux=${session.hasAuxData}, fileHistory=${session.hasFileHistory}, last=${session.lastTimestamp}",
            )
        }
        log.kv("selectedIds", selected.map { it.id })
    }

    private fun logSummary(
        log: ImportLog,
        imported: List<ImportedSession>,
        skipped: List<String>,
        failures: List<ImportFailure>,
        failedChecks: List<FailedCheck>,
        startedAt: Long,
    ) {
        log.section(ClaudeSessionsBundle.message("log.section.summary"))
        log.kv("imported", imported.size)
        for (session in imported) log.line("${session.action.uppercase()}  ${session.sourceId} -> ${session.writtenId}  (${session.projectFolderName})")
        log.kv("skipped", skipped.size)
        log.kv("failed", failures.size)
        for (failure in failures) log.line("FAILED  ${failure.sessionId}  ${failure.message}")
        log.kv("failedChecks", failedChecks.size)
        for (check in failedChecks) log.line(check.toString())
        log.kv("durationMs", System.currentTimeMillis() - startedAt)
    }
}
