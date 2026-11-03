package com.localbudget.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localbudget.app.domain.model.SpendingInsights
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

@Composable
internal fun FoodWeeklyCard(insights: SpendingInsights, formatter: NumberFormat) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("M/d") }
    val today = LocalDate.now()
    val largest = insights.foodWeeks.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
    val daysInMonth = insights.foodWeeks.firstOrNull()?.start?.let { start ->
        insights.foodWeeks.lastOrNull()?.end?.let { end -> ChronoUnit.DAYS.between(start, end) + 1 }
    } ?: 0L
    val weeklyAverage = if (daysInMonth > 0) (insights.foodTotal.toDouble() * 7 / daysInMonth).roundToLong() else 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("食費の週別内訳", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("月合計", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥${formatter.format(insights.foodTotal)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("週平均（7日換算）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("¥${formatter.format(weeklyAverage)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "週平均は月合計を月の日数で割り、7日分に換算しています。月をまたぐ週はこの月の分のみです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            insights.foodWeeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ongoing = today >= week.start && today <= week.end
                        Text(
                            "${week.start.format(dateFormat)}〜${week.end.format(dateFormat)}${if (ongoing) "（途中）" else ""}",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("¥${formatter.format(week.amount)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (week.amount.toFloat() / largest.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            Text("未登録の支出は含みません。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SubscriptionCandidatesCard(insights: SpendingInsights, formatter: NumberFormat, onImportStatement: () -> Unit) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy/M/d") }
    var expanded by remember(insights.subscriptions) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("サブスク・継続課金の候補", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "この月の候補合計 ¥${formatter.format(insights.subscriptionTotal)}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "直近6か月のカード明細から、この月まで3か月以上連続で、同じ利用先・金額の支払いが月1回ある、3,000円以下の明細を表示します。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (insights.subscriptions.isEmpty()) {
                Text("候補はまだありません。過去の明細を取り込むと確認できます。", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onImportStatement) { Text("カード明細を取り込む") }
            } else {
                val visible = if (expanded) insights.subscriptions else insights.subscriptions.take(5)
                visible.forEach { candidate ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(candidate.storeName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("¥${formatter.format(candidate.monthlyAmount)}/月", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "${candidate.charges.size}か月連続・直近 ${candidate.charges.last().date.format(dateFormat)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (insights.subscriptions.size > 5) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "閉じる" else "全${insights.subscriptions.size}件を見る")
                    }
                }
            }
            Text(
                "通常の買い物や公共料金が含まれる場合があります。年払い・金額が変わる契約は対象外です。候補合計は月の支出に含まれ、追加計上しません。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
