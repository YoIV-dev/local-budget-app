package com.localbudget.app.data

import com.localbudget.app.data.statement.StatementTextDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class StatementTextDecoderTest {

    private val text = "利用日,利用店名,利用金額\n2026/10/01,ｾﾌﾞﾝｲﾚﾌﾞﾝ,550\n"

    @Test
    fun shiftJisIsReadWhenTheBytesAreNotUtf8() {
        assertEquals(text, StatementTextDecoder.decode(text.toByteArray(Charset.forName("windows-31j"))))
    }

    @Test
    fun utf8WithAndWithoutBomIsRead() {
        assertEquals(text, StatementTextDecoder.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, StatementTextDecoder.decode(("\uFEFF" + text).toByteArray(Charsets.UTF_8)))
    }
}
