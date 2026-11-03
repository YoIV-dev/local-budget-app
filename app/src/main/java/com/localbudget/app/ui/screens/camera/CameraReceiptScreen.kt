package com.localbudget.app.ui.screens.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.ocr.ReceiptRegion
import com.localbudget.app.ui.screens.expenses.ExpenseAddEditDialog
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraReceiptScreen(
    viewModel: CameraReceiptViewModel,
    onNavigateBack: () -> Unit,
    galleryOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.processImageUri(context, uri)
        else if (galleryOnly) onNavigateBack()
    }

    LaunchedEffect(Unit) {
        if (galleryOnly) galleryLauncher.launch("image/*")
        else if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val ocrState by viewModel.ocrState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLiveScan by viewModel.isLiveScanEnabled.collectAsState()
    val isContinuous by viewModel.isContinuousMode.collectAsState()
    val detectionResult by viewModel.detectionResult.collectAsState()
    val batchExpenses by viewModel.batchExpenses.collectAsState()
    val showBatchReview by viewModel.showBatchReview.collectAsState()
    val askToContinue by viewModel.askToContinue.collectAsState()

    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    // Size of the preview the guide frame is drawn on, to read only the text inside the frame
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // One shot at a time: a second tap while focusing would otherwise queue another capture
    val isShooting = remember { AtomicBoolean(false) }

    // refocus: run autofocus/exposure on the guide frame (screen center) first, so a hand-held
    // manual shot is not taken while continuous AF is still hunting. Auto-capture skips it:
    // it only fires after the frame has stayed sharp and steady.
    val takePictureAction = { refocus: Boolean ->
        val capture = imageCapture
        if (capture != null && ocrState !is OcrUiState.Processing && isShooting.compareAndSet(false, true)) {
            val shoot = {
                capture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            isShooting.set(false)
                            viewModel.processImageProxy(image, viewfinderSize.width, viewfinderSize.height)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            isShooting.set(false)
                            exception.printStackTrace()
                        }
                    }
                )
            }
            val control = camera?.cameraControl
            if (!refocus || control == null) {
                shoot()
            } else {
                val center = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                val action = FocusMeteringAction.Builder(
                    center,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                // Completes when focus settles, fails or times out; shoot in every case
                control.startFocusAndMetering(action).addListener({ shoot() }, cameraExecutor)
            }
        }
    }

    // Dynamic guide box color based on detection result
    val guideColor by animateColorAsState(
        targetValue = when {
            !isLiveScan -> Color.White.copy(alpha = 0.8f)
            detectionResult.isReadyToCapture -> Color(0xFF00E676) // Bright green
            detectionResult.isReceiptDetected && !detectionResult.isSharp -> Color(0xFFFF9100) // Amber for blur
            else -> Color.White.copy(alpha = 0.7f)
        },
        label = "guideColor"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (galleryOnly) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { galleryLauncher.launch("image/*") }) { Text("画像を選ぶ") }
                TextButton(onClick = onNavigateBack) { Text("戻る", color = Color.White) }
            }
        } else if (hasCameraPermission) {
            // Camera Preview View with ImageAnalysis
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // Sharper, less noisy frames for OCR; the extra latency hides behind the steady-hold gate
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        imageCapture = capture

                        // Phase 3: ImageAnalysis for live scan
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val planes = imageProxy.planes
                                    if (planes.isNotEmpty()) {
                                        val yBuffer = planes[0].buffer
                                        viewModel.analyzeFrame(
                                            yBuffer = yBuffer,
                                            width = imageProxy.width,
                                            height = imageProxy.height,
                                            rowStride = planes[0].rowStride,
                                            onTriggerAutoCapture = {
                                                takePictureAction(false)
                                            }
                                        )
                                    }
                                    imageProxy.close()
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize().onSizeChanged { viewfinderSize = it }
            )

            // Viewfinder Guide Overlay (OCR keeps only the text inside this same frame)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val (left, top, right, bottom) = ReceiptRegion.guideFrame(size.width, size.height)

                drawRoundRect(
                    color = guideColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = if (detectionResult.isReadyToCapture) 3.5.dp.toPx() else 2.dp.toPx())
                )
            }

            // Top Bar Controls (Back, Flash, Modes)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                            tint = Color.White
                        )
                    }

                    // Live Scan & Continuous Mode Toggles
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isLiveScan,
                            onClick = { viewModel.toggleLiveScan() },
                            label = {
                                Text(
                                    text = if (isLiveScan) "ライブ検出: ON" else "ライブ検出: OFF",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                labelColor = Color.White,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )


                    }

                    IconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            camera?.cameraControl?.enableTorch(isFlashOn)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "フラッシュ切替",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Message Badge (Real-time detection feedback)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            detectionResult.isReadyToCapture -> Color(0xFF1B5E20).copy(alpha = 0.85f)
                            detectionResult.isReceiptDetected && !detectionResult.isSharp -> Color(0xFFE65100).copy(alpha = 0.85f)
                            else -> Color.Black.copy(alpha = 0.65f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (detectionResult.isReadyToCapture) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isLiveScan) detectionResult.statusMessage else "手動シャッターモード",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Control Bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(vertical = 24.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(52.dp))

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(4.dp, if (detectionResult.isReadyToCapture) Color(0xFF00E676) else Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (detectionResult.isReadyToCapture) Color(0xFF00E676) else Color.White)
                        .clickable { takePictureAction(true) }
                )

                // Batch Review Button or Placeholder
                if (batchExpenses.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.openBatchReview() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("一括確認 (${batchExpenses.size})", fontSize = 12.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.size(52.dp))
                }
            }
        } else {
            // Permission Request View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "カメラのアクセス権限が必要です",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "レシートを撮影してOCR解析を行うため、カメラへのアクセスを許可してください。\n画像データは端末外へ送信されません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("カメラ権限を許可する")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onNavigateBack) {
                    Text("戻る", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // Processing State Overlay
        if (ocrState is OcrUiState.Processing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isContinuous) "レシートを追加中..." else "レシートを解析中...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "端末内ML Kitで安全に読み取っています",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Error State Alert
        (ocrState as? OcrUiState.Error)?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("読み取りエラー") },
                text = { Text(err.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetState() }) {
                        Text("やり直す")
                    }
                }
            )
        }

        if (askToContinue) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("読み取り完了") },
                text = { Text("${batchExpenses.size}枚読み取りました。続けて読み取りますか？") },
                confirmButton = { Button(onClick = { viewModel.continueScanning() }) { Text("続けて読み取る") } },
                dismissButton = { TextButton(onClick = { viewModel.finishScanning() }) { Text("終了して確認") } }
            )
        }

        // Single Mode Confirmation Dialog
        (ocrState as? OcrUiState.Success)?.let { success ->
            val parsed = success.parsedReceipt
            val initialExpense = Expense(
                date = parsed.date,
                amount = parsed.amount,
                storeName = parsed.storeName,
                categoryId = parsed.suggestedCategoryId ?: categories.firstOrNull()?.id ?: 1L,
                memo = "",
                sourceType = SourceType.RECEIPT_OCR
            )

            ExpenseAddEditDialog(
                initialExpense = initialExpense,
                categories = categories,
                rawOcrText = parsed.rawText,
                amountWarning = parsed.amountWarning,
                onDismiss = { viewModel.discardScan(); if (galleryOnly) onNavigateBack() },
                onSave = { expense, items ->
                    viewModel.saveExpense(expense, items) {
                        onNavigateBack()
                    }
                },
                onSaveWithFridgeItems = { expense, fridgeItems, items ->
                    viewModel.saveExpenseWithFridgeItems(expense, fridgeItems, items) {
                        onNavigateBack()
                    }
                }
            )
        }

        // Continuous Mode Batch Review Dialog
        if (showBatchReview && batchExpenses.isNotEmpty()) {
            BatchReceiptReviewDialog(
                initialReceipts = batchExpenses,
                categories = categories,
                onDismiss = { viewModel.dismissBatchReview() },
                onSaveAll = { confirmedExpenses ->
                    viewModel.saveBatchExpenses(confirmedExpenses) {
                        onNavigateBack()
                    }
                }
            )
        }
    }
}
