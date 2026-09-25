package com.naveenapps.expensemanager.feature.transaction.import.review

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naveenapps.expensemanager.core.common.utils.toCompleteDateWithDate
import com.naveenapps.expensemanager.core.common.utils.toTimeAndMinutes
import com.naveenapps.expensemanager.core.designsystem.ui.components.AppCardView
import com.naveenapps.expensemanager.core.designsystem.ui.components.AppDatePickerDialog
import com.naveenapps.expensemanager.core.designsystem.ui.components.AppTimePickerDialog
import com.naveenapps.expensemanager.core.designsystem.ui.components.ClickableTextField
import com.naveenapps.expensemanager.core.designsystem.ui.components.SafeModalBottomSheet
import com.naveenapps.expensemanager.core.model.TransactionType
import com.naveenapps.expensemanager.feature.account.selection.AccountSelectionScreen
import com.naveenapps.expensemanager.feature.category.selection.CategorySelectionScreen
import com.naveenapps.expensemanager.feature.transaction.R
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ImportCardStack(
    state: ImportState,
    onAction: (ImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.currentDraft
    if (draft == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    R.string.import_all_reviewed,
                    state.acceptedCount,
                    state.rejectedCount,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    var showSwipeHint by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex > 0) {
            showSwipeHint = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.pendingDrafts.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(horizontal = 24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    ),
            )
        }

        if (showSwipeHint) {
            Text(
                text = stringResource(R.string.import_swipe_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        SwipeableImportCard(
            draft = draft,
            state = state,
            onAction = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onAction(ImportAction.RejectCurrent) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = stringResource(R.string.reject))
            }
            Button(
                onClick = { onAction(ImportAction.AcceptCurrent) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text(text = stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun SwipeableImportCard(
    draft: ImportDraft,
    state: ImportState,
    onAction: (ImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addAccessibilityLabel = stringResource(R.string.add)
    val skipAccessibilityLabel = stringResource(R.string.reject)
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 120.dp.toPx() }
    var rawOffset by remember(draft.parsed.id, state.currentIndex) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = rawOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "import_swipe",
    )
    val swipeFraction = (animatedOffset / swipeThreshold).coerceIn(-1f, 1f)
    val swipeBackgroundColor = when {
        swipeFraction > 0.1f -> MaterialTheme.colorScheme.primary
        swipeFraction < -0.1f -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val swipeContentColor = when {
        swipeFraction > 0.1f -> MaterialTheme.colorScheme.onPrimary
        swipeFraction < -0.1f -> MaterialTheme.colorScheme.onError
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(addAccessibilityLabel) {
                        onAction(ImportAction.AcceptCurrent)
                        true
                    },
                    CustomAccessibilityAction(skipAccessibilityLabel) {
                        onAction(ImportAction.RejectCurrent)
                        true
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(abs(swipeFraction).coerceIn(0f, 1f))
                .background(
                    swipeBackgroundColor.copy(alpha = if (swipeBackgroundColor == Color.Transparent) 0f else 0.85f),
                    androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (swipeFraction > 0) {
                    stringResource(R.string.add)
                } else if (swipeFraction < 0) {
                    stringResource(R.string.reject)
                } else {
                    ""
                },
                style = MaterialTheme.typography.headlineMedium,
                color = swipeContentColor,
            )
        }

        AppCardView(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(draft.parsed.id, state.currentIndex) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                rawOffset > swipeThreshold -> {
                                    rawOffset = 0f
                                    onAction(ImportAction.AcceptCurrent)
                                }

                                rawOffset < -swipeThreshold -> {
                                    rawOffset = 0f
                                    onAction(ImportAction.RejectCurrent)
                                }

                                else -> rawOffset = 0f
                            }
                        },
                        onHorizontalDrag = { _, delta ->
                            rawOffset = (rawOffset + delta).coerceIn(-swipeThreshold * 2, swipeThreshold * 2)
                        },
                    )
                },
        ) {
            EditableImportCardContent(
                draft = draft,
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun EditableImportCardContent(
    draft: ImportDraft,
    state: ImportState,
    onAction: (ImportAction) -> Unit,
) {
    val scroll = rememberScrollState()
    var showTransactionDetails by rememberSaveable(draft.parsed.id) { mutableStateOf(false) }

    if (draft.showDateSelection) {
        AppDatePickerDialog(
            selectedDate = draft.dateTime,
            onDateSelected = { picked ->
                val merged = mergeDateAndTime(picked, draft.dateTime)
                onAction(ImportAction.UpdateDateTime(draft.parsed.id, merged))
                onAction(ImportAction.DismissSelectors(draft.parsed.id))
            },
            onDismiss = { onAction(ImportAction.DismissSelectors(draft.parsed.id)) },
        )
    }
    if (draft.showTimeSelection) {
        val cal = Calendar.getInstance().apply { time = draft.dateTime }
        AppTimePickerDialog(
            reminderTimeState = Triple(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false,
            ),
            onTimeSelected = { triple ->
                val updated = mergeTime(draft.dateTime, triple.first, triple.second)
                onAction(ImportAction.UpdateDateTime(draft.parsed.id, updated))
                onAction(ImportAction.DismissSelectors(draft.parsed.id))
            },
            onDismiss = { onAction(ImportAction.DismissSelectors(draft.parsed.id)) },
        )
    }
    if (draft.showAccountSelection) {
        SafeModalBottomSheet(
            onDismissRequest = { onAction(ImportAction.DismissSelectors(draft.parsed.id)) },
        ) {
            AccountSelectionScreen(
                accounts = state.accounts,
                selectedAccount = draft.selectedAccount,
                createNewCallback = {},
                onItemSelection = { onAction(ImportAction.SelectAccount(draft.parsed.id, it)) },
            )
        }
    }
    if (draft.showCategorySelection) {
        SafeModalBottomSheet(
            onDismissRequest = { onAction(ImportAction.DismissSelectors(draft.parsed.id)) },
        ) {
            CategorySelectionScreen(
                categories = state.categories.filter {
                    if (draft.transactionType == TransactionType.INCOME) {
                        it.type.name == "INCOME"
                    } else {
                        it.type.name == "EXPENSE"
                    }
                }.ifEmpty { state.categories },
                selectedCategory = draft.selectedCategory,
                createNewCallback = {},
                onItemSelection = { onAction(ImportAction.SelectCategory(draft.parsed.id, it)) },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = draft.parsed.counterpartyName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    R.string.import_transaction_identity_format,
                    draft.dateTime.toCompleteDateWithDate(),
                    draft.dateTime.toTimeAndMinutes(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (draft.isDuplicate) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.import_possible_duplicate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.import_duplicate_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        val parseError: String? = draft.parsed.parseError
        if (parseError != null && !draft.dateManuallyCorrected) {
            Text(
                text = parseError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!draft.parsed.isSuccess) {
            Text(
                text = stringResource(R.string.import_bank_status_review, draft.parsed.status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = draft.amountText,
            onValueChange = { onAction(ImportAction.UpdateAmount(draft.parsed.id, it)) },
            label = { Text(stringResource(R.string.amount)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = draft.transactionType == TransactionType.EXPENSE,
                onClick = { onAction(ImportAction.UpdateType(draft.parsed.id, TransactionType.EXPENSE)) },
                label = { Text(stringResource(R.string.expense)) },
            )
            FilterChip(
                selected = draft.transactionType == TransactionType.INCOME,
                onClick = { onAction(ImportAction.UpdateType(draft.parsed.id, TransactionType.INCOME)) },
                label = { Text(stringResource(R.string.income)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClickableTextField(
                modifier = Modifier.weight(1f),
                value = draft.dateTime.toCompleteDateWithDate(),
                label = R.string.select_date,
                leadingIcon = null,
                onClick = { onAction(ImportAction.ShowDateSelection(draft.parsed.id)) },
            )
            ClickableTextField(
                modifier = Modifier.weight(1f),
                value = draft.dateTime.toTimeAndMinutes(),
                label = R.string.select_time,
                leadingIcon = null,
                onClick = { onAction(ImportAction.ShowTimeSelection(draft.parsed.id)) },
            )
        }

        OutlinedButton(
            onClick = { onAction(ImportAction.ShowAccountSelection(draft.parsed.id)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = draft.selectedAccount?.name ?: stringResource(R.string.select_account))
        }

        QuickCategoryPicker(
            draft = draft,
            state = state,
            onAction = onAction,
        )

        TextButton(
            onClick = { showTransactionDetails = !showTransactionDetails },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (showTransactionDetails) {
                    stringResource(R.string.import_hide_transaction_details)
                } else {
                    stringResource(R.string.import_transaction_details)
                },
            )
        }

        if (showTransactionDetails) {
            Text(
                text = stringResource(
                    R.string.import_transaction_details_format,
                    draft.parsed.counterpartyName,
                    draft.parsed.counterpartyVpa,
                    draft.parsed.bankName,
                    draft.parsed.accountNumber,
                    draft.parsed.referenceId,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = draft.notes,
            onValueChange = { onAction(ImportAction.UpdateNotes(draft.parsed.id, it)) },
            label = { Text(stringResource(R.string.notes)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
    }
}

@Composable
private fun QuickCategoryPicker(
    draft: ImportDraft,
    state: ImportState,
    onAction: (ImportAction) -> Unit,
) {
    val quickList = if (draft.transactionType == TransactionType.INCOME) {
        state.topIncomeCategories
    } else {
        state.topExpenseCategories
    }
    val visibleQuick = quickList.filter { it.id != draft.selectedCategory?.id }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.top_categories),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 28.dp),
            ) {
            draft.selectedCategory?.let { selected ->
                item(key = "selected_${selected.id}") {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = {
                            Text(
                                selected.titleResId?.let { stringResource(it) }
                                    ?: selected.name,
                            )
                        },
                    )
                }
            }
            items(
                count = visibleQuick.size,
                key = { index -> visibleQuick[index].id },
            ) { index ->
                val category = visibleQuick[index]
                FilterChip(
                    selected = false,
                    onClick = {
                        onAction(ImportAction.SelectCategory(draft.parsed.id, category))
                    },
                    label = {
                        Text(
                            category.titleResId?.let { stringResource(it) }
                                ?: category.name,
                        )
                    },
                )
            }
            item(key = "more") {
                androidx.compose.material3.AssistChip(
                    onClick = { onAction(ImportAction.ShowCategorySelection(draft.parsed.id)) },
                    label = { Text(stringResource(R.string.more_categories)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = null,
                        )
                    },
                )
            }
            }
            // Scroll affordance: fading edge hints the row continues.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(28.dp)
                    .height(32.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
        }
    }
}

private fun mergeDateAndTime(datePart: Date, timePart: Date): Date {    val dateCal = Calendar.getInstance().apply { time = datePart }
    val timeCal = Calendar.getInstance().apply { time = timePart }
    dateCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
    dateCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
    dateCal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
    return dateCal.time
}

private fun mergeTime(base: Date, hour: Int, minute: Int): Date {
    val cal = Calendar.getInstance().apply { time = base }
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    return cal.time
}
