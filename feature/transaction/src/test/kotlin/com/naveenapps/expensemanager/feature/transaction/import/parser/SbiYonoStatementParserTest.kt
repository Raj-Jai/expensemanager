package com.naveenapps.expensemanager.feature.transaction.import.parser

import com.google.common.truth.Truth.assertThat
import com.naveenapps.expensemanager.core.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fixtures are verbatim text layers from a real SBI YONO "Statement of Account"
 * PDF, including the column padding and the indented description wraps.
 */
class SbiYonoStatementParserTest {

    private val parser = SbiYonoStatementParser()

    private val statementHeader = """
        STATEMENT OF ACCOUNT
        State Bank of India
        Balance
    """.trimIndent()

    private val withdrawalRow = """
        WDL TFR
        01/06/2026   01/06/2026   UPI/DR/800000000101/MOCK MART         -       310.00         -      1,00,003.00
                                   /CNRB/0000000000/NO R
                                   0000000000000 AT 00000 TESTBR
    """.trimIndent()

    private val depositRow = """
        DEP TFR
        01/06/2026   01/06/2026   UPI/CR/800000001010/MOCK MART         -          -         103.00   1,00,002.00
                                   /SBIN/0000000000/Paym
                                   0000000000000 AT 00000 TESTBR
    """.trimIndent()

    private fun startOfDay(date: String) = LocalDate.parse(date)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun `parses withdrawal row with wrapped description`() {
        val result = parser.parse("$statementHeader\n$withdrawalRow")

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(310.00)
        assertThat(tx.transactionType).isEqualTo(TransactionType.EXPENSE)
        assertThat(tx.payOrCollect).isEqualTo("PAY")
        assertThat(tx.referenceId).isEqualTo("800000000101")
        assertThat(tx.receiverName).isEqualTo("MOCK MART")
        assertThat(tx.senderName).isEmpty()
        assertThat(tx.bankName).isEqualTo("State Bank of India")
        assertThat(tx.status).isEqualTo("SUCCESS")
        assertThat(tx.isSuccess).isTrue()
        assertThat(tx.parseError).isNull()
    }

    @Test
    fun `parses deposit row as income`() {
        val result = parser.parse("$statementHeader\n$depositRow")

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(103.00)
        assertThat(tx.transactionType).isEqualTo(TransactionType.INCOME)
        assertThat(tx.payOrCollect).isEqualTo("COLLECT")
        assertThat(tx.senderName).isEqualTo("MOCK MART")
        assertThat(tx.receiverName).isEmpty()
        assertThat(tx.referenceId).isEqualTo("800000001010")
    }

    @Test
    fun `row date is start of day and flagged date only`() {
        val result = parser.parse("$statementHeader\n$withdrawalRow")

        val tx = result.first()
        assertThat(tx.dateTime.time).isEqualTo(startOfDay("2026-06-01"))
        assertThat(tx.isDateOnly).isTrue()
    }

    @Test
    fun `keeps the transfer marker out of the previous description`() {
        val text = "$statementHeader\n$withdrawalRow\n$depositRow"
        val result = parser.parse(text)

        assertThat(result).hasSize(2)
        assertThat(result[0].rawText).doesNotContain("TFR")
        assertThat(result[1].rawText).doesNotContain("TFR")
    }

    @Test
    fun `description wraps stay with their own row`() {
        val text = "$statementHeader\n$withdrawalRow\n$depositRow"
        val result = parser.parse(text)

        assertThat(result[0].rawText).contains("0000000000000 AT 00000 TESTBR")
        assertThat(result[0].rawText).doesNotContain("MOCK MART")
        assertThat(result[1].rawText).contains("0000000000000 AT 00000 TESTBR")
    }

    @Test
    fun `parses indian grouped amounts`() {
        val row = "WDL TFR\n" +
            "04/06/2026   04/06/2026   UPI/DR/800000000707/MOCK MART P          -      1,00,004.00      -       1,00,007.00\n" +
            "                                   N/SBIN/0000000000/Ren\n" +
            "                                   0000000000000 AT 00000 TESTBR"

        val result = parser.parse("$statementHeader\n$row")

        assertThat(result).hasSize(1)
        assertThat(result.first().amount).isEqualTo(25000.00)
    }

    @Test
    fun `transfer marker decides direction when description ends with a dash`() {
        val row = "WDL TFR\n" +
            "05/06/2026   05/06/2026   ATM WDL -                            -       500.00         -       1,00,006.00"

        val result = parser.parse("$statementHeader\n$row")

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(500.00)
        assertThat(tx.transactionType).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `parses neft row beneficiary and urn`() {
        val row = "DEP TFR\n" +
            "09/06/2026   09/06/2026   NEFT*RBIS0PFMS01*RBISH00637          -          -       1,00,001.00   1,00,008.00\n" +
            "                                   704380*SAMPLE VENDOR*B\n" +
            "                                   0000000000000 AT 00000 TESTBR"

        val result = parser.parse("$statementHeader\n$row")

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(1000.00)
        assertThat(tx.transactionType).isEqualTo(TransactionType.INCOME)
        assertThat(tx.senderName).isEqualTo("SAMPLE VENDOR")
        assertThat(tx.referenceId).isEqualTo("RBIS0PFMS01*RBISH00637")
    }

    @Test
    fun `row without transfer marker is read from the money columns`() {
        val row = "25/06/2026   25/06/2026   INTEREST CREDIT                         -         -         520.00      1,00,005.00"

        val result = parser.parse("$statementHeader\n$row")

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(520.00)
        assertThat(tx.transactionType).isEqualTo(TransactionType.INCOME)
        assertThat(tx.senderName).isEqualTo("INTEREST CREDIT")
        assertThat(tx.referenceId).isEmpty()
    }

    @Test
    fun `skips page headers footers and balance captions`() {
        val text = listOf(
            statementHeader,
            withdrawalRow,
            "",
            "Page no. 1",
            "                                    Balance",
            "",
            depositRow,
            "                                   ( )                                           ( )             ( )               ( )",
            "Page no. 2",
        ).joinToString("\n")

        val result = parser.parse(text)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.referenceId })
            .containsExactly("800000000101", "800000001010").inOrder()
    }

    @Test
    fun `holder account number is read from the header when present`() {
        val header = "$statementHeader\nA/c No. XXXXXX0001\nBranch: TESTBR"
        val result = parser.parse("$header\n$withdrawalRow")

        assertThat(result.first().accountNumber).isEqualTo("XXXXXX0001")
    }

    @Test
    fun `holder account number is N slash A when the statement omits it`() {
        val result = parser.parse("$statementHeader\n$withdrawalRow")

        assertThat(result.first().accountNumber).isEqualTo("N/A")
    }

    @Test
    fun `counterparty vpa is unknown for a statement layout`() {
        val result = parser.parse("$statementHeader\n$withdrawalRow")

        val tx = result.first()
        assertThat(tx.senderVpa).isEqualTo("N/A")
        assertThat(tx.receiverVpa).isEqualTo("N/A")
        assertThat(tx.defaultNotes).isEqualTo("MOCK MART Ref:800000000101 PAY")
    }

    @Test
    fun `impossible date is flagged invalid instead of dropped`() {
        val row = "WDL TFR\n" +
            "31/02/2026   31/02/2026   UPI/DR/800000000101/MOCK MART        -       310.00         -      1,00,003.00"

        val result = parser.parse("$statementHeader\n$row")

        assertThat(result).hasSize(1)
        assertThat(result.first().parseError).isNotNull()
    }

    @Test
    fun `line without a money tail is not a transaction`() {
        val text = "$statementHeader\n01/06/2026   01/06/2026   UPI/DR/800000000101/MOCK MART"

        assertThat(parser.parse(text)).isEmpty()
    }

    @Test
    fun `blank text yields nothing`() {
        assertThat(parser.parse("   \n  ")).isEmpty()
    }

    @Test
    fun `canHandle detects a yono account statement`() {
        assertThat(parser.canHandle("$statementHeader\n$withdrawalRow")).isTrue()
    }

    @Test
    fun `canHandle rejects a bhim transaction history`() {
        val bhim = "Transaction History\nDate Time Bank Name Payment ID/Reference Number Pay/Collect"

        assertThat(parser.canHandle(bhim)).isFalse()
    }

    @Test
    fun `canHandle requires the statement header`() {
        assertThat(parser.canHandle(withdrawalRow)).isFalse()
    }

    @Test
    fun `concurrent parsing yields correct dates`() = runBlocking {
        val text = "$statementHeader\n$withdrawalRow"
        val expected = startOfDay("2026-06-01")

        val results = (1..16).map {
            async(Dispatchers.Default) { parser.parse(text) }
        }.awaitAll()

        results.forEach { parsed ->
            assertThat(parsed).hasSize(1)
            assertThat(parsed.first().dateTime.time).isEqualTo(expected)
        }
    }
}
