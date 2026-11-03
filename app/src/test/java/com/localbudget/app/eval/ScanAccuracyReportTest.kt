package com.localbudget.app.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the parser/extractor over an annotated receipt set and writes a report.
 * Paths come from system properties set in app/build.gradle.kts (see eval/README.md):
 *   ./gradlew :app:testDebugUnitTest --tests '*ScanAccuracyReportTest*'
 */
class ScanAccuracyReportTest {

    private val reportRoot = File(System.getProperty("scanEval.reportDir") ?: "build/reports/scan-eval")

    /** Private real-receipt set in eval/data (gitignored); skipped when absent. */
    @Test
    fun reportOnLocalDataset() {
        val dataDir = File(System.getProperty("scanEval.dataDir") ?: "../eval/data")
        assumeTrue("No dataset at $dataDir", dataDir.isDirectory)

        val loaded = ScanDataset.load(dataDir)
        assumeTrue("No receipts with ocr.txt in $dataDir", loaded.samples.isNotEmpty())

        val results = loaded.samples.map { ScanEvaluator.evaluate(it) }
        val outDir = File(reportRoot, "data")
        ScanReportWriter.write(outDir, results, loaded.missingOcr)
        println("Scan accuracy report: ${File(outDir, "report.md").absolutePath}")
    }

    /** Committed synthetic samples in eval/samples: keeps the loader and report path exercised. */
    @Test
    fun reportOnCommittedSamples() {
        val samplesDir = File(System.getProperty("scanEval.samplesDir") ?: "../eval/samples")
        val loaded = ScanDataset.load(samplesDir)
        assertEquals(6, loaded.samples.size)

        val results = loaded.samples.map { ScanEvaluator.evaluate(it) }
        val outDir = File(reportRoot, "samples")
        ScanReportWriter.write(outDir, results, loaded.missingOcr)

        assertTrue(File(outDir, "report.md").readText().contains("| 全体 | 6 |"))
        assertTrue(File(outDir, "summary.json").isFile)
    }
}
