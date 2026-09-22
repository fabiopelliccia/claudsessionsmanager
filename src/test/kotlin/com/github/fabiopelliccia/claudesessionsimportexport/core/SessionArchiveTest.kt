package com.github.fabiopelliccia.claudesessionsimportexport.core

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

class SessionArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A Windows path only survives inside a JSON string with its backslashes escaped. */
    private fun String.asJsonString(): String = replace("\\", "\\\\")

    private fun writeSourceSession(home: java.nio.file.Path, id: String, cwd: String): SessionInfo {
        val projectDir = home.resolve("projects").resolve(ClaudePaths.encodeProjectPath(cwd))
        Files.createDirectories(projectDir)
        Files.write(
            projectDir.resolve("$id.jsonl"),
            listOf(
                """{"type":"summary","summary":"Demo session","sessionId":"$id"}""",
                """{"type":"user","message":{"role":"user","content":"hi"},"timestamp":"2026-01-01T00:00:00.000Z","cwd":"${cwd.asJsonString()}","sessionId":"$id"}""",
            ),
        )
        val auxDir = projectDir.resolve(id).resolve("tool-results")
        Files.createDirectories(auxDir)
        Files.writeString(auxDir.resolve("result.json"), """{"ok":true}""")

        return SessionScanner.listSessions(home).single { it.id == id }
    }

    @Test
    fun `exports and imports a session into a fresh home`() {
        val sourceHome = tmp.newFolder("source", ".claude").toPath()
        val cwd = "C:\\Users\\fabio\\demo"
        val session = writeSourceSession(sourceHome, "session-1", cwd)

        val archive = tmp.root.toPath().resolve("archive.zip")
        val exportOutcome = SessionArchive.export(listOf(session), archive, home = sourceHome)
        assertEquals(1, exportOutcome.exportedCount)
        assertTrue(Files.exists(archive))

        val targetHome = tmp.newFolder("target", ".claude").toPath()
        val importOutcome = SessionArchive.import(archive, home = targetHome)

        assertEquals(1, importOutcome.imported.size)
        val written = importOutcome.imported.single()
        assertEquals("session-1", written.writtenId)
        assertEquals("new", written.action)

        val targetTranscript = targetHome.resolve("projects")
            .resolve(ClaudePaths.encodeProjectPath(cwd))
            .resolve("session-1.jsonl")
        assertTrue(Files.exists(targetTranscript))
        assertTrue(Files.readString(targetTranscript).contains("Demo session") || Files.readString(targetTranscript).contains("\"hi\""))

        val targetAuxFile = targetHome.resolve("projects")
            .resolve(ClaudePaths.encodeProjectPath(cwd))
            .resolve("session-1")
            .resolve("tool-results")
            .resolve("result.json")
        assertTrue(Files.exists(targetAuxFile))
    }

    @Test
    fun `importing twice duplicates under a new id instead of overwriting`() {
        val sourceHome = tmp.newFolder("source2", ".claude").toPath()
        val cwd = "C:\\Users\\fabio\\demo2"
        val session = writeSourceSession(sourceHome, "session-2", cwd)

        val archive = tmp.root.toPath().resolve("archive2.zip")
        SessionArchive.export(listOf(session), archive, home = sourceHome)

        val targetHome = tmp.newFolder("target2", ".claude").toPath()
        val first = SessionArchive.import(archive, home = targetHome)
        assertEquals("new", first.imported.single().action)

        val second = SessionArchive.import(archive, home = targetHome, conflictPolicy = ConflictPolicy.DUPLICATE)
        val duplicated = second.imported.single()
        assertEquals("duplicated", duplicated.action)
        assertTrue(duplicated.writtenId != "session-2")

        val projectDir = targetHome.resolve("projects").resolve(ClaudePaths.encodeProjectPath(cwd))
        assertTrue(Files.exists(projectDir.resolve("session-2.jsonl")))
        assertTrue(Files.exists(projectDir.resolve("${duplicated.writtenId}.jsonl")))
        // The duplicated transcript must reference its own new id, not the original one.
        assertTrue(Files.readString(projectDir.resolve("${duplicated.writtenId}.jsonl")).contains(duplicated.writtenId))
    }

    @Test
    fun `importing into a different machine's folder remaps the project path`() {
        val sourceHome = tmp.newFolder("source4", ".claude").toPath()
        val sourceCwd = "C:\\Users\\fabio\\demo4"
        val session = writeSourceSession(sourceHome, "session-4", sourceCwd)

        val archive = tmp.root.toPath().resolve("archive4.zip")
        SessionArchive.export(listOf(session), archive, home = sourceHome)

        // Simulates a colleague importing the archive on another machine (different drive/user),
        // where the folder Claude Code encoded from the original `cwd` doesn't exist at all.
        val targetHome = tmp.newFolder("target4", ".claude").toPath()
        val targetCwd = "D:\\Work\\colleague\\demo4"
        val outcome = SessionArchive.import(archive, home = targetHome, targetProjectPath = targetCwd)

        val written = outcome.imported.single()
        assertEquals(ClaudePaths.encodeProjectPath(targetCwd), written.projectFolderName)

        val transcript = targetHome.resolve("projects")
            .resolve(ClaudePaths.encodeProjectPath(targetCwd))
            .resolve("session-4.jsonl")
        assertTrue(Files.exists(transcript))

        val userLine = Files.readAllLines(transcript).map { JsonParser.parseString(it).asJsonObject }
            .single { it.has("cwd") }
        assertEquals(targetCwd, userLine.get("cwd").asString)
    }

    @Test
    fun `imported timestamps are shifted to look recent while keeping their spacing`() {
        val sourceHome = tmp.newFolder("source5", ".claude").toPath()
        val cwd = "C:\\Users\\fabio\\demo5"
        val projectDir = sourceHome.resolve("projects").resolve(ClaudePaths.encodeProjectPath(cwd))
        Files.createDirectories(projectDir)
        Files.write(
            projectDir.resolve("session-5.jsonl"),
            listOf(
                """{"type":"user","message":{"role":"user","content":"first"},"timestamp":"2020-01-01T00:00:00.000Z","cwd":"${cwd.asJsonString()}","sessionId":"session-5"}""",
                """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"second"}]},"timestamp":"2020-01-01T00:00:05.000Z","sessionId":"session-5"}""",
            ),
        )
        val session = SessionScanner.listSessions(sourceHome).single()

        val archive = tmp.root.toPath().resolve("archive5.zip")
        SessionArchive.export(listOf(session), archive, home = sourceHome)

        val targetHome = tmp.newFolder("target5", ".claude").toPath()
        SessionArchive.import(archive, home = targetHome)

        val transcript = targetHome.resolve("projects")
            .resolve(ClaudePaths.encodeProjectPath(cwd))
            .resolve("session-5.jsonl")
        val timestamps = Files.readAllLines(transcript)
            .map { JsonParser.parseString(it).asJsonObject.get("timestamp").asString }
            .map { Instant.parse(it) }

        // The gap between the two messages must survive the shift...
        assertEquals(Duration.ofSeconds(5), Duration.between(timestamps[0], timestamps[1]))
        // ...and the whole session must now land close to "now", not in 2020.
        assertTrue(Duration.between(timestamps[1], Instant.now()).abs() < Duration.ofMinutes(1))
    }

    @Test
    fun `shiftTimestampsToNow = false leaves timestamps exactly as recorded`() {
        val sourceHome = tmp.newFolder("source6", ".claude").toPath()
        val cwd = "C:\\Users\\fabio\\demo6"
        val session = writeSourceSession(sourceHome, "session-6", cwd)

        val archive = tmp.root.toPath().resolve("archive6.zip")
        SessionArchive.export(listOf(session), archive, home = sourceHome)

        val targetHome = tmp.newFolder("target6", ".claude").toPath()
        SessionArchive.import(archive, home = targetHome, shiftTimestampsToNow = false)

        val transcript = targetHome.resolve("projects")
            .resolve(ClaudePaths.encodeProjectPath(cwd))
            .resolve("session-6.jsonl")
        assertTrue(Files.readString(transcript).contains("2026-01-01T00:00:00.000Z"))
    }

    @Test
    fun `SKIP policy leaves an already present session untouched`() {
        val sourceHome = tmp.newFolder("source3", ".claude").toPath()
        val cwd = "C:\\Users\\fabio\\demo3"
        val session = writeSourceSession(sourceHome, "session-3", cwd)

        val archive = tmp.root.toPath().resolve("archive3.zip")
        SessionArchive.export(listOf(session), archive, home = sourceHome)

        val targetHome = tmp.newFolder("target3", ".claude").toPath()
        SessionArchive.import(archive, home = targetHome)

        val second = SessionArchive.import(archive, home = targetHome, conflictPolicy = ConflictPolicy.SKIP)
        assertTrue(second.imported.isEmpty())
        assertEquals(listOf("session-3"), second.skipped)
        assertFalse(
            Files.list(targetHome.resolve("projects").resolve(ClaudePaths.encodeProjectPath(cwd))).use { it.count() } > 2,
        )
    }
}
