package com.localbudget.app.ui.screens.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCategories: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val scanLogEnabled by viewModel.scanLogEnabled.collectAsState()
    val scanLogCount by viewModel.scanLogCount.collectAsState()
    var confirmClearScanLog by remember { mutableStateOf(false) }
    val fridgeReminderEnabled by viewModel.fridgeReminderEnabled.collectAsState()
    val fridgeReminderTime by viewModel.fridgeReminderTime.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshScanLogCount() }

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // SAF Document Launchers
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                viewModel.exportJson(stream)
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                viewModel.exportCsv(stream)
            }
        }
    }

    val exportScanLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.exportScanLog { context.contentResolver.openOutputStream(it) } }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setFridgeReminderEnabled(true)
        } else {
            viewModel.showMessage("通知が許可されなかったため、お知らせはオフのままです")
        }
    }

    fun setFridgeReminder(enabled: Boolean) {
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setFridgeReminderEnabled(enabled)
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The app's outer Scaffold already pads for the status and navigation bars
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Design
            item {
                Text(
                    text = "デザイン",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionCard(
                        icon = Icons.Default.Category,
                        title = "カテゴリ管理",
                        description = "支出カテゴリの追加・アイコン・色・予算上限を設定します",
                        onClick = onNavigateToCategories
                    )
                }
            }

            // Section 2: Data Backup & Restore
            item {
                Text(
                    text = "データ管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = LocalDate.now().toString()

                    SettingsActionCard(
                        icon = Icons.Default.CloudUpload,
                        title = "JSONバックアップを保存",
                        description = "全データを完全な構造化ファイルとしてエクスポートします",
                        onClick = { exportJsonLauncher.launch("local_budget_backup_$today.json") }
                    )

                    SettingsActionCard(
                        icon = Icons.Default.CloudDownload,
                        title = "JSONバックアップから復元",
                        description = "エクスポートしたバックアップファイルからデータをインポートします",
                        onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) }
                    )

                    SettingsActionCard(
                        icon = Icons.Default.TableChart,
                        title = "CSVファイルを出力 (Excel対応)",
                        description = "支出明細を表計算ソフトで閲覧できる形式で出力します",
                        onClick = { exportCsvLauncher.launch("local_budget_expenses_$today.csv") }
                    )
                }
            }

            // Section: Daily fridge reminder (Phase 2c)
            item {
                Text(
                    text = "お知らせ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                // Turned off in system settings after being switched on here
                val blocked = fridgeReminderEnabled && !NotificationManagerCompat.from(context).areNotificationsEnabled()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "冷蔵庫の確認",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "指定した時刻ごろ、確認したい食材や在庫チェックがあれば通知します。確認することがない日は通知しません。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (blocked) {
                                Text(
                                    text = "端末の設定で通知がオフになっているため、表示されません",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(onClick = {
                                TimePickerDialog(context, { _, hour, minute ->
                                    viewModel.setFridgeReminderTime(LocalTime.of(hour, minute))
                                }, fridgeReminderTime.hour, fridgeReminderTime.minute, true).show()
                            }) {
                                Text("通知時刻  ${fridgeReminderTime.format(DateTimeFormatter.ofPattern("HH:mm"))}  変更")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = fridgeReminderEnabled,
                            onCheckedChange = { setFridgeReminder(it) }
                        )
                    }
                }
            }

            // Section: Scan verification mode (Phase 1d, eval/README.md)
            item {
                Text(
                    text = "スキャン精度の検証",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "検証モード",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "レシートの写真・読み取り結果・確認画面で直した内容を端末内に記録します（記録済み ${scanLogCount} 件）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = scanLogEnabled,
                                onCheckedChange = { viewModel.setScanLogEnabled(it) }
                            )
                        }
                    }

                    if (scanLogCount > 0) {
                        SettingsActionCard(
                            icon = Icons.Default.FolderZip,
                            title = "検証ログを書き出す",
                            description = "記録をZIPファイルに保存します。レシートの写真を含むので、共有先に注意してください",
                            onClick = {
                                exportScanLogLauncher.launch("local_budget_scan_log_${LocalDate.now()}.zip")
                            }
                        )
                        SettingsActionCard(
                            icon = Icons.Default.DeleteOutline,
                            title = "検証ログを削除",
                            description = "端末内の記録 ${scanLogCount} 件をすべて削除します",
                            onClick = { confirmClearScanLog = true }
                        )
                    }
                }
            }

            // Section 3: Privacy & Security Statement
            item {
                Text(
                    text = "プライバシー & セキュリティ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "家計の記録とレシートの読み取りは端末内で",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "支出履歴や在庫データは端末内に保存し、レシートの読み取りも端末内で行います。これらの記録を開発者のサーバーへ送信する機能はありません。\n\n共有・書き出し先は利用者が選べます。Androidの設定によっては、アプリのデータがクラウドにバックアップされる場合があります。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Section 4: App Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("local-budget", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "v1.0.0 (MIT License)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (confirmClearScanLog) {
        AlertDialog(
            onDismissRequest = { confirmClearScanLog = false },
            title = { Text("検証ログの削除") },
            text = { Text("記録した ${scanLogCount} 件の写真と読み取り結果をすべて削除します。元に戻せません。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearScanLog()
                    confirmClearScanLog = false
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClearScanLog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // Import Confirmation Dialog
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("データの復元") },
            text = { Text("選択したバックアップファイルからデータをインポートしますか？\n既存のデータに追加・マージされます。") },
            confirmButton = {
                Button(onClick = {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        viewModel.importJson(stream)
                    }
                    pendingImportUri = null
                }) {
                    Text("復元を実行")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingImportUri = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // Processing Overlay
    if (isProcessing) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("処理中...", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
