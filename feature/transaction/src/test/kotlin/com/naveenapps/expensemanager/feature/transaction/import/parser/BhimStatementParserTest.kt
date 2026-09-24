package com.naveenapps.expensemanager.feature.transaction.import.parser

import com.google.common.truth.Truth.assertThat
import com.naveenapps.expensemanager.core.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BhimStatementParserTest {

    private val parser = BhimStatementParser()

    private val sampleHeader = """
        Transaction History
        Customer Mobile Number: ++91XXXXXXXXX  Transaction History from 24/06/2026 to 24/09/2026
        Date       Time             Bank Name           Account Number                        Sender                                             Receiver                       Payment ID/Reference Number   Pay/Collect   Amount (in Rs.)    DR/CR        Status
    """.trimIndent()

    @Test
    fun `parses debit expense row`() {
        val line = "23/09/2026   01:38:31       State Bank Of India     XXXXXX0001                  mockparty03@upi(MOCK PARTY 02)                       mockparty05@oksbi(xxxxxxxxndal)                  800000000202              PAY             100.00          DR       SUCCESS"
        val result = parser.parse("$sampleHeader\n$line")

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(100.0)
        assertThat(tx.transactionType).isEqualTo(TransactionType.EXPENSE)
        assertThat(tx.referenceId).isEqualTo("800000000202")
        assertThat(tx.senderVpa).isEqualTo("mockparty03@upi")
        assertThat(tx.senderName).isEqualTo("MOCK PARTY 02")
        assertThat(tx.receiverVpa).isEqualTo("mockparty05@oksbi")
        assertThat(tx.bankName).isEqualTo("State Bank Of India")
        assertThat(tx.accountNumber).isEqualTo("XXXXXX0001")
        assertThat(tx.status).isEqualTo("SUCCESS")
        assertThat(tx.isSuccess).isTrue()
        assertThat(tx.parseError).isNull()
    }

    @Test
    fun `parses credit income row`() {
        val line = "21/09/2026   01:06:35       State Bank Of India     XXXXXX0001             mockparty04@hdfcbank(NPCI BHIM)                            mockparty03@upi()                           800000000404              PAY             20.00            CR      SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(20.0)
        assertThat(tx.transactionType).isEqualTo(TransactionType.INCOME)
        assertThat(tx.counterpartyVpa).isEqualTo("mockparty04@hdfcbank")
    }

    @Test
    fun `parses collect row and icici account`() {
        val line = "20/09/2026   04:45:52   ICICI BANK CREDIT CARD    000000XXXXXX00   xxxxxxxxxxxxxxxxxxxxxxxxxxxd1fb1@upi(MOCK PARTY 02)            xxxxxtore1.bd@axisbank(Google Play)                 800000000909           COLLECT             2.00           DR       SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.payOrCollect).isEqualTo("COLLECT")
        assertThat(tx.accountNumber).isEqualTo("000000XXXXXX00")
        assertThat(tx.receiverName).isEqualTo("Google Play")
    }

    @Test
    fun `parses N-A receiver as missing counterparty`() {
        val line = "31/08/2026   02:33:44       State Bank Of India     XXXXXX0001                  mockparty03@upi(MOCK PARTY 02)                                     N/AN/A                                800000000303              PAY            3300.00          DR       SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(3300.0)
        assertThat(tx.receiverVpa).isEqualTo("N/A")
        assertThat(tx.referenceId).isEqualTo("800000000303")
    }

    @Test
    fun `keeps failure status for user review`() {
        val line = "04/09/2026   21:13:42   ICICI BANK CREDIT CARD    000000XXXXXX00              mockparty03@upi(MOCK PARTY 02)                      mockparty06@rapl(xxxxxxxxREST)                  800000000808              PAY            1650.60          DR       FAILURE"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        assertThat(result.first().isSuccess).isFalse()
        assertThat(result.first().status).isEqualTo("FAILURE")
    }

    @Test
    fun `parses indian grouped amount with commas`() {
        val line = "22/07/2026   16:22:47      State Bank Of India     XXXXXX0001                   mockparty03@upi(MOCK PARTY 02)                   mockparty02@utkarshbank(xxxxxxxxsits)     800000000606   PAY   100000.00   DR   SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        assertThat(result.first().amount).isEqualTo(100000.0)
    }

    @Test
    fun `skips headers and footers`() {
        val text = "$sampleHeader\nPage no. 1\n\n"
        assertThat(parser.parse(text)).isEmpty()
    }

    @Test
    fun `canHandle detects bhim statement`() {
        assertThat(parser.canHandle(sampleHeader)).isTrue()
        assertThat(parser.canHandle("random text")).isFalse()
    }

    @Test
    fun `default notes contain counterparty and ref`() {
        val line = "23/09/2026   01:38:31       State Bank Of India     XXXXXX0001                  mockparty03@upi(MOCK PARTY 02)                       mockparty05@oksbi(xxxxxxxxndal)                  800000000202              PAY             100.00          DR       SUCCESS"
        val tx = parser.parse(line).first()
        assertThat(tx.defaultNotes).contains("800000000202")
    }

    @Test
    fun `parses single-space pdfbox layout`() {
        val line = "23/09/2026 01:38:31 State Bank Of India XXXXXX0001 mockparty03@upi(MOCK PARTY 02) mockparty05@oksbi(xxxxxxxxndal) 800000000202 PAY 100.00 DR SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(100.0)
        assertThat(tx.bankName).isEqualTo("State Bank Of India")
        assertThat(tx.accountNumber).isEqualTo("XXXXXX0001")
        assertThat(tx.senderVpa).isEqualTo("mockparty03@upi")
        assertThat(tx.receiverVpa).isEqualTo("mockparty05@oksbi")
    }

    @Test
    fun `parses single-space N-A receiver`() {
        val line = "31/08/2026 02:33:44 State Bank Of India XXXXXX0001 mockparty03@upi(MOCK PARTY 02) N/AN/A 800000000303 PAY 3300.00 DR SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        assertThat(result.first().receiverVpa).isEqualTo("N/A")
        assertThat(result.first().referenceId).isEqualTo("800000000303")
    }

    @Test
    fun `merchant text resembling delimiters does not misparse`() {
        val line = "16/07/2026   13:57:52      State Bank Of India     XXXXXX0001               mockparty03@upi(MOCK PARTY 02)                      mockparty01@hdfcbank(MOCK PARTY 06)                  800000000505   PAY    16.57     DR   SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        val tx = result.first()
        assertThat(tx.amount).isEqualTo(16.57)
        assertThat(tx.referenceId).isEqualTo("800000000505")
        assertThat(tx.receiverName).isEqualTo("MOCK PARTY 06")
    }

    @Test
    fun `malformed tail is skipped`() {
        val line = "16/07/2026   13:57:52      State Bank Of India     XXXXXX0001               mockparty03@upi(MOCK PARTY 02)                      mockparty01@hdfcbank(MOCK PARTY 05)"
        assertThat(parser.parse(line)).isEmpty()
    }

    @Test
    fun `impossible date is flagged invalid`() {
        val line = "99/99/2026   01:38:31       State Bank Of India     XXXXXX0001                  mockparty03@upi(MOCK PARTY 02)                       mockparty05@oksbi(xxxxxxxxndal)                  800000000202              PAY             100.00          DR       SUCCESS"
        val result = parser.parse(line)

        assertThat(result).hasSize(1)
        assertThat(result.first().parseError).isNotNull()
    }

    @Test
    fun `concurrent parsing yields correct dates`() {
        val line = "23/09/2026   01:38:31       State Bank Of India     XXXXXX0001                  mockparty03@upi(MOCK PARTY 02)                       mockparty05@oksbi(xxxxxxxxndal)                  800000000202              PAY             100.00          DR       SUCCESS"
        val results = runBlocking {
            (1..20).map {
                async(Dispatchers.Default) {
                    parser.parse(line).first()
                }
            }.awaitAll()
        }

        assertThat(results).hasSize(20)
        results.forEach {
            assertThat(it.amount).isEqualTo(100.0)
            assertThat(it.parseError).isNull()
            val cal = java.util.Calendar.getInstance().apply { time = it.dateTime }
            assertThat(cal.get(java.util.Calendar.YEAR)).isEqualTo(2026)
            assertThat(cal.get(java.util.Calendar.MONTH)).isEqualTo(java.util.Calendar.SEPTEMBER)
            assertThat(cal.get(java.util.Calendar.DAY_OF_MONTH)).isEqualTo(23)
        }
    }
}
