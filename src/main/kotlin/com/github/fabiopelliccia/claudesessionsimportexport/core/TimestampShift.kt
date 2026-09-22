package com.github.fabiopelliccia.claudesessionsimportexport.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shifts ISO-8601 instant strings (`"2026-01-01T10:00:00.000Z"`) by a fixed [Duration], preserving
 * the shape Claude Code writes. Used on import so a session lands around "now" instead of when it
 * was originally recorded, while the spacing between its own messages stays exactly the same -
 * every timestamp of a session moves by the same delta.
 */
object TimestampShift {

    // Locale.ROOT keeps digits plain ASCII regardless of the JVM's default locale.
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    /** The delta that moves [anchor] to "now" - `Duration.ZERO` when [anchor] can't be parsed. */
    fun deltaToNow(anchor: String?): Duration {
        val instant = anchor?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return Duration.ZERO
        return Duration.between(instant, Instant.now())
    }

    /** Returns the shifted value, or `null` when [value] isn't a parseable instant or [delta] is zero. */
    fun shift(value: String, delta: Duration): String? {
        if (delta.isZero) return null
        val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return null
        return FORMATTER.format(instant.plus(delta))
    }
}
