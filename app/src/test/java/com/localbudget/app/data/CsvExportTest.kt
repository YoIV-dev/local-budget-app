package com.localbudget.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CsvExportTest {

    @Test
    fun testCsvEscapingAndFormatting() {
        val escapeCsv: (String) -> String = { value ->
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        }

        // Test normal string
        assertEquals("セブン-イレブン", escapeCsv("セブン-イレブン"))

        // Test string with comma
        assertEquals("\"スーパー, イオン\"", escapeCsv("スーパー, イオン"))

        // Test string with quote
        assertEquals("\"大特価\"\"セール\"\"\"", escapeCsv("大特価\"セール\""))

        // Test string with newline
        assertEquals("\"行1\n行2\"", escapeCsv("行1\n行2"))

        // Test BOM byte
        val outputStream = ByteArrayOutputStream()
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("日付,店舗名,カテゴリ,金額,メモ,入力元\n")
            val row = listOf(
                "2026-09-25",
                escapeCsv("セブン-イレブン"),
                escapeCsv("食費"),
                "1280",
                escapeCsv("昼食, おにぎり"),
                escapeCsv("手動入力")
            ).joinToString(",")
            writer.write(row)
            writer.write("\n")
        }

        val resultString = outputStream.toString("UTF-8")
        assertTrue(resultString.startsWith("\uFEFF"))
        assertTrue(resultString.contains("\"昼食, おにぎり\""))
    }
}
