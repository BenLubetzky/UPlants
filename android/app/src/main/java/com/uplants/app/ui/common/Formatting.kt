package com.uplants.app.ui.common

import android.text.format.DateUtils
import java.time.OffsetDateTime
import java.util.Locale

/** "5 minutes ago", "Yesterday", "3 Nov" ... from the services' ISO-8601 timestamps. */
fun relativeTime(iso: String, now: Long = System.currentTimeMillis()): String {
    val millis = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull() ?: return ""
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return "just now"
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()
}

/** 999 → "999", 1200 → "1.2K", 3400000 → "3.4M". */
fun compactCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0).replace(".0K", "K")
    else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0).replace(".0M", "M")
}

fun formatRating(value: Double): String = String.format(Locale.US, "%.1f", value)
