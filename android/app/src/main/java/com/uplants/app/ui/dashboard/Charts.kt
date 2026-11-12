package com.uplants.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uplants.app.data.model.DayCount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Views per day as vertical bars, with the first and last date under the axis. Drawn with Canvas, no library. */
@Composable
fun ViewsPerDayChart(days: List<DayCount>, modifier: Modifier = Modifier) {
    val max = (days.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Text("$max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = "Views per day, last ${days.size} days, total ${days.sumOf { it.count }}" },
        ) {
            if (days.isEmpty()) return@Canvas
            val slot = size.width / days.size
            val barWidth = (slot * 0.7f).coerceAtLeast(2f)
            days.forEachIndexed { i, day ->
                val h = if (day.count == 0) 2.dp.toPx() else size.height * day.count / max
                drawRoundRect(
                    color = if (day.count == 0) empty else bar,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortDate(days.firstOrNull()?.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortDate(days.lastOrNull()?.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ratings by number of stars (0-5): one labelled bar each, the count above it. */
@Composable
fun RatingHistogramChart(histogram: List<Int>, modifier: Modifier = Modifier) {
    val max = (histogram.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.tertiary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        histogram.forEachIndexed { stars, count ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$count", style = MaterialTheme.typography.labelMedium)
                Canvas(Modifier.height(110.dp).width(28.dp)) {
                    val h = if (count == 0) 2.dp.toPx() else size.height * count / max
                    drawRoundRect(
                        color = if (count == 0) empty else bar,
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
                Text("$stars★", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun shortDate(iso: String?): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}.getOrDefault("")
