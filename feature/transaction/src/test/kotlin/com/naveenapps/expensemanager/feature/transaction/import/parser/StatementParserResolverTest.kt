package com.naveenapps.expensemanager.feature.transaction.import.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatementParserResolverTest {

    private val bhimText = """
        Transaction History
        Customer Mobile Number: +91XXXXXXXXX  Transaction History from 24/06/2026 to 24/09/2026
        Date       Time             Bank Name           Account Number      Sender      Receiver      Payment ID/Reference Number   Pay/Collect   Amount (in Rs.)    DR/CR        Status
        23/09/2026   01:38:31       State Bank Of India     XXXXXX0001                  mockparty03@upi(MOCK PARTY 02)                       mockparty05@oksbi(xxxxxxxxndal)                  800000000202              PAY             100.00          DR       SUCCESS
    """.trimIndent()

    private val yonoText = """
        STATEMENT OF ACCOUNT
        State Bank of India
        WDL TFR
        01/06/2026   01/06/2026   UPI/DR/800000000101/MOCK MART         -       310.00         -      1,00,003.00
    """.trimIndent()

    private val resolver = StatementParserResolver()

    @Test
    fun `selects the bhim parser for a transaction history`() {
        assertThat(resolver.select(bhimText)).isInstanceOf(BhimStatementParser::class.java)
    }

    @Test
    fun `selects the yono parser for an account statement`() {
        assertThat(resolver.select(yonoText)).isInstanceOf(SbiYonoStatementParser::class.java)
    }

    @Test
    fun `returns null for an unrecognised statement`() {
        assertThat(resolver.select("total 12 apples")).isNull()
    }

    @Test
    fun `built in parsers parse the statements they claim`() {
        val bhim = resolver.select(bhimText)?.parse(bhimText).orEmpty()
        val yono = resolver.select(yonoText)?.parse(yonoText).orEmpty()

        assertThat(bhim).hasSize(1)
        assertThat(bhim.first().amount).isEqualTo(100.0)
        assertThat(yono).hasSize(1)
        assertThat(yono.first().amount).isEqualTo(310.0)
    }

    @Test
    fun `reports the supported formats`() {
        assertThat(resolver.supportedFormats)
            .containsExactly("BHIM transaction history", "SBI YONO account statement").inOrder()
    }
}
