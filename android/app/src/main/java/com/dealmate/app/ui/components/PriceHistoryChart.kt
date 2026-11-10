package com.dealmate.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dealmate.app.R
import com.dealmate.app.domain.model.PricePoint
import com.dealmate.app.domain.util.Money
import com.dealmate.app.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lowest final price recorded at each past price check. Titled as HISTORY and captioned so it is not
 * mistaken for current offers. Points without a final price are not drawn (never invented).
 */
@Composable
fun PriceHistoryChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    val valid = points
        .filter { it.lowestFinalPrice != null && it.recordedAt != null }
        .sortedBy { it.recordedAt }
    Card(modifier = modifier.fillMaxWidth().testTag("price_history"), shape = MaterialTheme.shapes.medium, colors = cardColors(), border = cardBorder()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row {
                Text(
                    stringResource(R.string.history_chart_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (points.any { it.isMock }) DemoTag()
            }
            Text(
                stringResource(R.string.history_chart_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            if (valid.size < 2) {
                Text(
                    stringResource(R.string.history_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
                return@Column
            }
            val prices = valid.map { it.lowestFinalPrice!! }
            val min = prices.min()
            val max = prices.max()
            val lineColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.outlineVariant
            val dotFill = MaterialTheme.colorScheme.surfaceContainerLow

            Row(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.history_chart_max, Money.format(max)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 6.dp)) {
                val w = size.width
                val h = size.height
                val pad = 5.dp.toPx()
                val range = (max - min).coerceAtLeast(1).toFloat()
                fun x(i: Int) = pad + (w - 2 * pad) * i / (valid.size - 1)
                fun y(price: Long) = if (max == min) h / 2 else pad + (h - 2 * pad) * (1f - (price - min) / range)

                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                drawLine(gridColor, Offset(0f, pad), Offset(w, pad), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                drawLine(gridColor, Offset(0f, h - pad), Offset(w, h - pad), strokeWidth = 1.dp.toPx(), pathEffect = dash)

                val path = Path()
                prices.forEachIndexed { i, price ->
                    if (i == 0) path.moveTo(x(i), y(price)) else path.lineTo(x(i), y(price))
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                prices.forEachIndexed { i, price ->
                    drawCircle(dotFill, radius = 4.dp.toPx(), center = Offset(x(i), y(price)))
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x(i), y(price)), style = Stroke(2.dp.toPx()))
                }
            }
            Text(
                stringResource(R.string.history_chart_min, Money.format(min)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            val format = DateTimeFormatter.ofPattern("dd/MM")
            val zone = ZoneId.systemDefault()
            Row(Modifier.fillMaxWidth()) {
                Text(
                    format.format(valid.first().recordedAt!!.atZone(zone)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    format.format(valid.last().recordedAt!!.atZone(zone)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
