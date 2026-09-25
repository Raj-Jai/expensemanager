package com.naveenapps.expensemanager.feature.transaction.import.review

import com.google.common.truth.Truth.assertThat
import com.naveenapps.expensemanager.core.domain.usecase.account.GetAllAccountsUseCase
import com.naveenapps.expensemanager.core.domain.usecase.category.GetAllCategoryUseCase
import com.naveenapps.expensemanager.core.domain.usecase.settings.currency.GetCurrencyUseCase
import com.naveenapps.expensemanager.core.domain.usecase.settings.currency.GetFormattedAmountUseCase
import com.naveenapps.expensemanager.core.domain.usecase.transaction.AddTransactionUseCase
import com.naveenapps.expensemanager.core.model.Amount
import com.naveenapps.expensemanager.core.model.Currency
import com.naveenapps.expensemanager.core.model.ParsedTransaction
import com.naveenapps.expensemanager.core.model.TransactionType
import com.naveenapps.expensemanager.core.navigation.AppComposeNavigator
import com.naveenapps.expensemanager.core.repository.TransactionRepository
import com.naveenapps.expensemanager.core.settings.domain.repository.NumberFormatRepository
import com.naveenapps.expensemanager.core.testing.BaseCoroutineTest
import com.naveenapps.expensemanager.core.testing.FAKE_EXPENSE_TRANSACTION
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest : BaseCoroutineTest() {

    private val getCurrencyUseCase: GetCurrencyUseCase = mock()
    private val getAllAccountsUseCase: GetAllAccountsUseCase = mock()
    private val getAllCategoryUseCase: GetAllCategoryUseCase = mock()
    private val getFormattedAmountUseCase: GetFormattedAmountUseCase = mock()
    private val addTransactionUseCase: AddTransactionUseCase = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val numberFormatRepository: NumberFormatRepository = mock()
    private val appComposeNavigator: AppComposeNavigator = mock()

    private fun createViewModel(): ImportViewModel {
        whenever(getCurrencyUseCase.invoke()).thenReturn(flowOf(Currency(symbol = "₹", name = "INR")))
        whenever(getAllAccountsUseCase.invoke()).thenReturn(flowOf(emptyList()))
        whenever(getAllCategoryUseCase.invoke()).thenReturn(flowOf(emptyList()))
        whenever(getFormattedAmountUseCase.invoke(0.0, Currency(symbol = "₹", name = "INR"))).thenReturn(
            Amount(0.0, "₹0.00"),
        )
        return ImportViewModel(
            getCurrencyUseCase = getCurrencyUseCase,
            getAllAccountsUseCase = getAllAccountsUseCase,
            getAllCategoryUseCase = getAllCategoryUseCase,
            getFormattedAmountUseCase = getFormattedAmountUseCase,
            addTransactionUseCase = addTransactionUseCase,
            transactionRepository = transactionRepository,
            numberFormatRepository = numberFormatRepository,
            appComposeNavigator = appComposeNavigator,
        )
    }

    @Test
    fun `duplicate detected on same amount date notes`() {
        val vm = createViewModel()
        val existing = FAKE_EXPENSE_TRANSACTION.copy(
            amount = Amount(100.0),
            createdOn = Date(1_700_000_000_000L),
            notes = "Shop Ref:123 PAY",
        )
        val parsed = ParsedTransaction(
            amount = 100.0,
            transactionType = TransactionType.EXPENSE,
            dateTime = Date(1_700_000_000_000L),
            senderVpa = "a@upi",
            senderName = "A",
            receiverVpa = "Shop",
            receiverName = "Shop",
            referenceId = "123",
            bankName = "SBI",
            accountNumber = "X1",
            payOrCollect = "PAY",
            status = "SUCCESS",
        )

        val isDup = vm.isDuplicate(
            parsed.copy(),
            listOf(existing.copy(notes = "Wrong notes")),
        )
        assertThat(isDup).isFalse()

        val parsedWithNotes = parsed.copy()
        val existingMatching = existing.copy(notes = parsedWithNotes.defaultNotes)
        assertThat(vm.isDuplicate(parsedWithNotes, listOf(existingMatching))).isTrue()
    }

    @Test
    fun `amount tolerance treats near-equal as duplicate`() {        val vm = createViewModel()
        val base = Date(1_700_000_000_000L)
        val parsed = ParsedTransaction(
            amount = 100.004,
            transactionType = TransactionType.EXPENSE,
            dateTime = base,
            senderVpa = "a",
            senderName = "",
            receiverVpa = "b",
            receiverName = "Shop",
            referenceId = "1",
            bankName = "",
            accountNumber = "",
            payOrCollect = "PAY",
            status = "SUCCESS",
        )
        val existing = FAKE_EXPENSE_TRANSACTION.copy(
            amount = Amount(100.0),
            createdOn = base,
            notes = parsed.defaultNotes,
        )
        assertThat(vm.isDuplicate(parsed, listOf(existing))).isTrue()
    }

    private fun draftFor(parsed: ParsedTransaction) = ImportDraft(
        parsed = parsed,
        amountText = parsed.amount.toString(),
        notes = parsed.defaultNotes,
        dateTime = parsed.dateTime,
        transactionType = parsed.transactionType,
        selectedAccount = null,
        selectedCategory = null,
    )

    private fun parsedWith(status: String, parseError: String? = null) = ParsedTransaction(
        amount = 10.0,
        transactionType = TransactionType.EXPENSE,
        dateTime = Date(1_700_000_000_000L),
        senderVpa = "a",
        senderName = "",
        receiverVpa = "b",
        receiverName = "Shop",
        referenceId = "1",
        bankName = "SBI",
        accountNumber = "X1",
        payOrCollect = "PAY",
        status = status,
        parseError = parseError,
    )

    @Test
    fun `failed rows are not importable`() {
        assertThat(draftFor(parsedWith("FAILURE")).isImportable).isFalse()
    }

    @Test
    fun `rows with parse errors are not importable until corrected`() {
        val draft = draftFor(parsedWith("SUCCESS", "Could not parse date - please correct"))
        assertThat(draft.isImportable).isFalse()
        assertThat(draft.copy(dateManuallyCorrected = true).isImportable).isTrue()
    }

    @Test
    fun `successful rows are importable`() {
        assertThat(draftFor(parsedWith("SUCCESS")).isImportable).isTrue()
    }

    @Test
    fun `valid selection count excludes unavailable rows`() {
        val available = draftFor(parsedWith("SUCCESS")).copy(isSelected = true)
        val unavailable = draftFor(parsedWith("FAILURE")).copy(isSelected = true)
        val state = ImportState(
            drafts = listOf(available, unavailable),
            acceptedCount = 1,
            rejectedCount = 0,
        )

        assertThat(state.validSelectedCount).isEqualTo(1)
        assertThat(state.readyCount).isEqualTo(1)
        assertThat(state.unavailableCount).isEqualTo(1)
    }

    @Test
    fun `bulk confirmation is disabled without valid selection or while saving`() {
        val available = draftFor(parsedWith("SUCCESS")).copy(isSelected = true)
        val unavailable = draftFor(parsedWith("FAILURE")).copy(isSelected = true)

        assertThat(ImportState(drafts = listOf(available, unavailable)).canConfirmSelection).isTrue()
        assertThat(ImportState(drafts = listOf(unavailable)).canConfirmSelection).isFalse()
        assertThat(
            ImportState(
                drafts = listOf(available),
                isSaving = true,
            ).canConfirmSelection,
        ).isFalse()
    }

    @Test
    fun `view mode switching preserves review state`() {
        val vm = createViewModel()

        vm.processAction(ImportAction.SwitchToList)
        assertThat(vm.state.value.viewMode).isEqualTo(ImportViewMode.LIST)

        vm.processAction(ImportAction.SwitchToCard)
        assertThat(vm.state.value.viewMode).isEqualTo(ImportViewMode.CARD)
    }

    @Test
    fun `review progress uses accepted and rejected counts`() {
        val state = ImportState(
            drafts = listOf(
                draftFor(parsedWith("SUCCESS")),
                draftFor(parsedWith("SUCCESS")),
                draftFor(parsedWith("FAILURE")),
                draftFor(parsedWith("FAILURE")),
            ),
            acceptedCount = 1,
            rejectedCount = 1,
        )

        assertThat(state.reviewedCount).isEqualTo(2)
        assertThat(state.reviewProgress).isEqualTo(0.5f)
    }

    @Test
    fun `start parsing shows loading`() {
        val vm = createViewModel()

        vm.processAction(ImportAction.StartParsing)

        assertThat(vm.state.value.isLoading).isTrue()
    }

    @Test
    fun `top categories rank by usage within type`() {
        val vm = createViewModel()
        val expenseA = com.naveenapps.expensemanager.core.testing.FAKE_CATEGORY.copy(id = "a")
        val expenseB = com.naveenapps.expensemanager.core.testing.FAKE_CATEGORY.copy(id = "b")
        val incomeC = com.naveenapps.expensemanager.core.testing.FAKE_CATEGORY.copy(
            id = "c",
            type = com.naveenapps.expensemanager.core.model.CategoryType.INCOME,
        )
        val existing = listOf(
            FAKE_EXPENSE_TRANSACTION.copy(categoryId = "b"),
            FAKE_EXPENSE_TRANSACTION.copy(categoryId = "b"),
            FAKE_EXPENSE_TRANSACTION.copy(categoryId = "a"),
        )

        val top = vm.topCategoriesFor(
            TransactionType.EXPENSE,
            existing,
            listOf(expenseA, expenseB, incomeC),
        )

        assertThat(top.map { it.id }).containsExactly("b", "a").inOrder()
    }

    @Test
    fun `top categories exclude other types and cap at five`() {
        val vm = createViewModel()
        val cats = (1..8).map {
            com.naveenapps.expensemanager.core.testing.FAKE_CATEGORY.copy(id = "e$it")
        }

        val top = vm.topCategoriesFor(TransactionType.EXPENSE, emptyList(), cats)

        assertThat(top).hasSize(5)
        assertThat(top.map { it.id }).containsExactly("e1", "e2", "e3", "e4", "e5").inOrder()
    }
}
