package com.localbudget.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.model.CategoryExpenseSummary
import com.localbudget.app.domain.model.CategoryTrend
import com.localbudget.app.ui.theme.parseColorHex
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DonutChart(
    summaries: List<CategoryExpenseSummary>,
    totalAmount: Long,
    modifier: Modifier = Modifier,
    chartSize: Dp = 200.dp,
    strokeWidth: Dp = 28.dp
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(summaries) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 650))
    }

    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    Box(
        modifier = modifier.size(chartSize),
        contentAlignment = Alignment.Center
    ) {
        val emptyColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.size(chartSize)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = size.minDimension
            val radius = (canvasSize - strokePx) / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f
            )
            val arcSize = Size(radius * 2, radius * 2)

            if (summaries.isEmpty() || totalAmount == 0L) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )
            } else {
                var currentAngle = -90f
                summaries.forEach { item ->
                    val sweepAngle = item.percentage * 360f * progress.value
                    if (sweepAngle > 0.5f) {
                        drawArc(
                            color = parseColorHex(item.category.colorHex),
                            startAngle = currentAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                        )
                        currentAngle += sweepAngle
                    }
                }
            }
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "合計支出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "¥${currencyFormatter.format(totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Months of spending as bars stacked in each category's color, the trend's largest category at the
 * bottom, with a legend of the categories underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyStackedBarChart(
    trend: CategoryTrend,
    currentMonthLabel: String,
    modifier: Modifier = Modifier,
    maxBarHeight: Dp = 100.dp
) {
    if (trend.months.isEmpty()) return

    val maxAmount = (trend.months.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(1L)
    val primaryColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSubtle = MaterialTheme.colorScheme.onSurfaceVariant

    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Sized by its content rather than a fixed height, so labels are not clipped at large font sizes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            trend.months.forEach { month ->
                val ratio = (month.total.toFloat() / maxAmount.toFloat()).coerceIn(0.05f, 1f)
                val isCurrent = month.label == currentMonthLabel

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (month.total > 0) {
                        Text(
                            text = "¥${currencyFormatter.format(month.total)}",
                            fontSize = 9.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) primaryColor else textSubtle,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(maxBarHeight * ratio)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        if (month.total == 0L) {
                            drawRect(color = emptyColor)
                        } else {
                            var bottom = size.height
                            month.amounts.forEach { (category, amount) ->
                                val segmentHeight = size.height * amount / month.total
                                drawRect(
                                    color = parseColorHex(category.colorHex),
                                    topLeft = Offset(0f, bottom - segmentHeight),
                                    size = Size(size.width, segmentHeight)
                                )
                                bottom -= segmentHeight
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = month.label,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) textPrimary else textSubtle
                    )
                }
            }
        }

        if (trend.categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                trend.categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(parseColorHex(category.colorHex), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = category.name, fontSize = 11.sp, color = textSubtle)
                    }
                }
            }
        }
    }
}
