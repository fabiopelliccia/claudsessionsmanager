package com.github.fabiopelliccia.claudesessionsimportexport.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaudePathsTest {

    @Test
    fun `encodes every non-alphanumeric character as a dash`() {
        assertEquals(
            "C--Users-pelliccia-IdeaProjects-Demo",
            ClaudePaths.encodeProjectPath("C:\\Users\\pelliccia\\IdeaProjects\\Demo"),
        )
        assertEquals(
            "-Users-fabio-my-project",
            ClaudePaths.encodeProjectPath("/Users/fabio/my-project"),
        )
    }
}
