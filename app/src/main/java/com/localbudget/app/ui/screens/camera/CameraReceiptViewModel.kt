package com.localbudget.app.ui.screens.camera

import android.content.Context
import android.graphics.ImageFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.localbudget.app.data.ocr.GalleryReceiptImage
import com.localbudget.app.data.ocr.ReceiptOcrEngine
import com.localbudget.app.data.scanlog.ScanLogStore
import com.localbudget.app.domain.food.ReceiptFoodExtractor
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.ParsedReceipt
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.ocr.AutoCaptureGate
import com.localbudget.app.domain.ocr.DetectionResult
import com.localbudget.app.domain.ocr.ReceiptDetector
import com.localbudget.app.domain.ocr.ReceiptItemParser
import com.localbudget.app.domain.ocr.ReceiptRegion
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import com.localbudget.app.domain.repository.FridgeRepository
import com.localbudget.app.domain.scanlog.ConfirmedReceipt
import com.localbudget.app.domain.scanlog.ScanLogEntry
import com.localbudget.app.domain.scanlog.ScanSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

sealed interface OcrUiState {
    object Idle : OcrUiState
    object Processing : OcrUiState
    data class Success(val parsedReceipt: ParsedReceipt) : OcrUiState
    data class Error(val message: String) : OcrUiState
}

class CameraReceiptViewModel(
    private val ocrEngine: ReceiptOcrEngine,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val fridgeRepository: FridgeRepository? = null,
    private val scanLogEnabled: StateFlow<Boolean>? = null,
    private val scanLogStore: ScanLogStore? = null
) : ViewModel() {

    private val _ocrState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 3 Features
    val isLiveScanEnabled = MutableStateFlow(true)
    val isContinuousMode = MutableStateFlow(true)
    val askToContinue = MutableStateFlow(false)
    private var lastBatchParsed: ParsedReceipt? = null

    private val _detectionResult = MutableStateFlow(DetectionResult())
    val detectionResult: StateFlow<DetectionResult> = _detectionResult.asStateFlow()

    // Continuous capture queue
    private val _batchExpenses = MutableStateFlow<List<BatchReceipt>>(emptyList())
    val batchExpenses: StateFlow<List<BatchReceipt>> = _batchExpenses.asStateFlow()

    val showBatchReview = MutableStateFlow(false)

    private val autoCaptureGate = AutoCaptureGate()
    // What the detector actually measured, before the hint text / colors are adjusted for the screen
    @Volatile private var lastRawDetection = DetectionResult()

    // Verification mode: the scan on the confirmation screen, recorded once the user saves or closes it
    private class PendingScan(
        val capturedAtMillis: Long,
        val source: ScanSource,
        val detection: DetectionResult?,
        val rotationDegrees: Int,
        val image: ByteArray?,
        val parsed: ParsedReceipt
    )
    private var pendingScan: PendingScan? = null
    // Set on the analyzer thread, read on the capture callback thread
    @Volatile private var autoCaptureRequested = false

    private val isScanLogOn: Boolean
        get() = scanLogStore != null && scanLogEnabled?.value == true

    fun toggleLiveScan() {
        isLiveScanEnabled.value = !isLiveScanEnabled.value
        autoCaptureGate.reset()
        if (!isLiveScanEnabled.value) {
            _detectionResult.value = DetectionResult(statusMessage = "手動シャッターモード")
        }
    }

    /**
     * Called by ImageAnalysis.Analyzer on each preview frame
     */
    fun analyzeFrame(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        onTriggerAutoCapture: () -> Unit
    ) {
        // Only while the viewfinder is in front: not during OCR, the confirmation dialog or batch review
        if (!isLiveScanEnabled.value || _ocrState.value !is OcrUiState.Idle || showBatchReview.value || askToContinue.value) {
            return
        }

        val result = ReceiptDetector.analyzeLuminance(yBuffer, width, height, rowStride)
        val now = System.currentTimeMillis()
        val fire = autoCaptureGate.onFrame(result, now)
        lastRawDetection = result
        _detectionResult.value = when {
            autoCaptureGate.isWaitingForNewReceipt() ->
                result.copy(isReceiptDetected = false, statusMessage = "撮影済み。次のレシートに替えてください")
            result.isReadyToCapture ->
                result.copy(statusMessage = "そのまま静止… ${(autoCaptureGate.progress(now) * 100).toInt()}%")
            else -> result
        }

        if (fire) {
            autoCaptureRequested = true
            onTriggerAutoCapture()
        }
    }

    /**
     * [viewWidth]/[viewHeight]: size of the viewfinder the guide frame was drawn on, so only the text
     * inside the frame is read (0 when unknown: read the whole image).
     */
    @OptIn(ExperimentalGetImage::class)
    fun processImageProxy(imageProxy: ImageProxy, viewWidth: Int = 0, viewHeight: Int = 0) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            _ocrState.value = OcrUiState.Error("画像の取得に失敗しました")
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        // Copied before ML Kit gets the image: wrapping it moves the buffer to its end
        val jpeg = if (isScanLogOn) jpegBytes(imageProxy) else null
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        val guideFrame = if (viewWidth > 0 && viewHeight > 0) {
            // ML Kit reports boxes in the upright image, i.e. after applying the rotation
            val swapped = rotation == 90 || rotation == 270
            val uprightWidth = if (swapped) imageProxy.height else imageProxy.width
            val uprightHeight = if (swapped) imageProxy.width else imageProxy.height
            listOf(ReceiptRegion.guideFrameInImage(viewWidth, viewHeight, uprightWidth, uprightHeight))
        } else {
            emptyList()
        }
        val source = if (autoCaptureRequested) ScanSource.AUTO_CAPTURE else ScanSource.MANUAL_CAPTURE
        autoCaptureRequested = false
        val detection = lastRawDetection.takeIf { isLiveScanEnabled.value }

        val continuous = isContinuousMode.value
        _ocrState.value = OcrUiState.Processing

        viewModelScope.launch {
            try {
                val parsed = ocrEngine.processInputImage(inputImage, guideFrame)
                if (continuous) {
                    queueForBatch(parsed)
                    askToContinue.value = true
                    _ocrState.value = OcrUiState.Idle
                } else {
                    holdForScanLog(source, detection, rotation, jpeg, parsed)
                    _ocrState.value = OcrUiState.Success(parsed)
                }
            } catch (e: Exception) {
                _ocrState.value = OcrUiState.Error(e.localizedMessage ?: "OCR処理に失敗しました")
            } finally {
                imageProxy.close()
            }
        }
    }

    fun processImageUri(context: Context, uri: Uri) {
        val continuous = false
        _ocrState.value = OcrUiState.Processing
        viewModelScope.launch {
            try {
                // No guide frame here: read only the receipt paper found in the photo
                val picked = withContext(Dispatchers.IO) { GalleryReceiptImage.load(context, uri) }
                val parsed = ocrEngine.processBitmap(picked.bitmap, picked.rotationDegrees, picked.receiptRegions)
                if (continuous) {
                    queueForBatch(parsed)
                    _ocrState.value = OcrUiState.Idle
                } else {
                    val image = if (isScanLogOn) {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }
                    } else {
                        null
                    }
                    holdForScanLog(ScanSource.GALLERY, null, 0, image, parsed)
                    _ocrState.value = OcrUiState.Success(parsed)
                }
            } catch (e: Exception) {
                _ocrState.value = OcrUiState.Error(e.localizedMessage ?: "画像の読み込みに失敗しました")
            }
        }
    }

    private fun queueForBatch(parsed: ParsedReceipt) {
        lastBatchParsed = parsed
        val expense = Expense(
            date = parsed.date,
            amount = parsed.amount,
            storeName = parsed.storeName,
            categoryId = parsed.suggestedCategoryId ?: categories.value.firstOrNull()?.id ?: 1L,
            sourceType = SourceType.RECEIPT_OCR
        )
        _batchExpenses.value = _batchExpenses.value +
            BatchReceipt(expense, ReceiptItemParser.parse(parsed.rawText), parsed.amountWarning)
    }

    fun continueScanning() { askToContinue.value = false }

    fun finishScanning() {
        askToContinue.value = false
        val parsed = lastBatchParsed
        if (_batchExpenses.value.size == 1 && parsed != null) {
            _batchExpenses.value = emptyList()
            lastBatchParsed = null
            _ocrState.value = OcrUiState.Success(parsed)
        } else {
            openBatchReview()
        }
    }

    fun openBatchReview() {
        if (_batchExpenses.value.isNotEmpty()) {
            showBatchReview.value = true
        }
    }

    fun dismissBatchReview() {
        showBatchReview.value = false
    }

    fun saveBatchExpenses(receipts: List<BatchReceipt>, onSaved: () -> Unit) {
        viewModelScope.launch {
            receipts.forEach { receipt ->
                val expenseId = expenseRepository.insertReceiptExpense(
                    receipt.expense.copy(sourceType = SourceType.RECEIPT_OCR),
                    receipt.items
                )
                val foods = receipt.fridgeItems().map { it.copy(expenseId = expenseId) }
                if (foods.isNotEmpty()) fridgeRepository?.insertFridgeItems(foods)
            }
            _batchExpenses.value = emptyList()
            showBatchReview.value = false
            onSaved()
        }
    }

    /** [editedItems]: the scanned lines with prices the user corrected, null to save them as read. */
    fun saveExpense(expense: Expense, editedItems: List<ReceiptLineItem>?, onSaved: () -> Unit) {
        val items = editedItems ?: scannedItems()
        recordScan(expense, emptyList())
        viewModelScope.launch {
            expenseRepository.insertReceiptExpense(expense.copy(sourceType = SourceType.RECEIPT_OCR), items)
            _ocrState.value = OcrUiState.Idle
            onSaved()
        }
    }

    fun saveExpenseWithFridgeItems(
        expense: Expense,
        fridgeItems: List<FridgeItem>,
        editedItems: List<ReceiptLineItem>?,
        onSaved: () -> Unit
    ) {
        val items = editedItems ?: scannedItems()
        recordScan(expense, fridgeItems)
        viewModelScope.launch {
            val expenseId = expenseRepository.insertReceiptExpense(expense.copy(sourceType = SourceType.RECEIPT_OCR), items)
            if (fridgeItems.isNotEmpty() && fridgeRepository != null) {
                val itemsWithExpenseId = fridgeItems.map { it.copy(expenseId = expenseId) }
                fridgeRepository.insertFridgeItems(itemsWithExpenseId)
            }
            _ocrState.value = OcrUiState.Idle
            onSaved()
        }
    }

    /** The lines of the receipt on the confirmation screen, saved along with its expense. */
    private fun scannedItems(): List<ReceiptLineItem> {
        val parsed = (_ocrState.value as? OcrUiState.Success)?.parsedReceipt ?: return emptyList()
        return ReceiptItemParser.parse(parsed.rawText)
    }

    fun resetState() {
        // Retrying after a cancel or error should be able to capture the same receipt again
        autoCaptureGate.reset()
        _ocrState.value = OcrUiState.Idle
    }

    /** Confirmation screen closed without saving. */
    fun discardScan() {
        recordScan(null, emptyList())
        resetState()
    }

    private fun holdForScanLog(
        source: ScanSource,
        detection: DetectionResult?,
        rotationDegrees: Int,
        image: ByteArray?,
        parsed: ParsedReceipt
    ) {
        pendingScan = if (isScanLogOn) {
            PendingScan(System.currentTimeMillis(), source, detection, rotationDegrees, image, parsed)
        } else {
            null
        }
    }

    /** Writes the pending scan with what the user saved ([expense] null = discarded). */
    private fun recordScan(expense: Expense?, keptFood: List<FridgeItem>) {
        val scan = pendingScan ?: return
        val store = scanLogStore ?: return
        pendingScan = null
        val entry = ScanLogEntry(
            capturedAtMillis = scan.capturedAtMillis,
            source = scan.source,
            detection = scan.detection,
            rotationDegrees = scan.rotationDegrees,
            parsed = scan.parsed,
            predictedFood = ReceiptFoodExtractor.extractLines(scan.parsed.rawText),
            confirmed = expense?.let {
                ConfirmedReceipt(it.storeName, it.date, it.amount, it.categoryId, keptFood.map { f -> f.name })
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            // A failed log write must never block saving the expense itself
            runCatching { store.save(entry, scan.image) }
        }
    }

    /** The captured JPEG as-is (in-memory ImageCapture output is a single JPEG plane). */
    private fun jpegBytes(imageProxy: ImageProxy): ByteArray? {
        if (imageProxy.format != ImageFormat.JPEG) return null
        val buffer = imageProxy.planes.firstOrNull()?.buffer?.duplicate() ?: return null
        // From the start whatever already read it; the duplicate leaves the plane's own position alone
        buffer.rewind()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes.takeIf { it.isNotEmpty() }
    }

    class Factory(
        private val ocrEngine: ReceiptOcrEngine,
        private val expenseRepository: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
        private val fridgeRepository: FridgeRepository? = null,
        private val scanLogEnabled: StateFlow<Boolean>? = null,
        private val scanLogStore: ScanLogStore? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CameraReceiptViewModel(
                ocrEngine, expenseRepository, categoryRepository, fridgeRepository, scanLogEnabled, scanLogStore
            ) as T
        }
    }
}
