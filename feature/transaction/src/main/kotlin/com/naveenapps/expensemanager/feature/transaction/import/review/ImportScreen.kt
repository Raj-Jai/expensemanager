package com.naveenapps.expensemanager.feature.transaction.import.review

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naveenapps.expensemanager.core.designsystem.ui.components.ExpenseManagerTopAppBar
import com.naveenapps.expensemanager.feature.transaction.R
import com.naveenapps.expensemanager.feature.transaction.import.parser.BhimPdfTextExtractor
import com.naveenapps.expensemanager.feature.transaction.import.parser.PdfExtractResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImportTransactionsScreen(
    viewModel: ImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isCompact = LocalConfiguration.current.screenWidthDp <= 480
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processAction(ImportAction.StartParsing)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    BhimPdfTextExtractor.extractText(context, uri)
                }
                when (result) {
                    is PdfExtractResult.Text -> {
                        viewModel.processAction(ImportAction.ParsedTextReceived(result.text))
                    }

                    is PdfExtractResult.Failure -> {
                        viewModel.processAction(ImportAction.ParseFailed(result.reason))
                    }
                }
            }
        }
    }

    val reviewedProgressText = stringResource(
        R.string.import_progress_format,
        state.reviewedCount,
        state.totalCount,
    )
    val undoLabel = stringResource(R.string.import_undo)
    val lastActionMessage = when (state.lastAction) {
        ImportReviewAction.ADDED -> stringResource(R.string.import_added)
        ImportReviewAction.SKIPPED -> stringResource(R.string.import_skipped)
        null -> null
    }

    LaunchedEffect(state.lastAction, state.currentIndex) {
        val message = lastActionMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "$message $reviewedProgressText",
            actionLabel = if (state.canUndo) undoLabel else null,
            duration = SnackbarDuration.Short,
            withDismissAction = false,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.processAction(ImportAction.UndoLast)
        } else {
            viewModel.processAction(ImportAction.ClearLastAction)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.processAction(ImportAction.ClearError)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(8.dp),
            )
        },
        topBar = {
            ExpenseManagerTopAppBar(
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationBackClick = { viewModel.processAction(ImportAction.ClosePage) },
                title = stringResource(R.string.import_pdf_title),
                actions = {
                    if (state.drafts.isNotEmpty()) {
                        IconButton(onClick = { pdfPicker.launch("application/pdf") }) {
                            Icon(
                                imageVector = Icons.Outlined.Upload,
                                contentDescription = stringResource(R.string.import_replace_pdf),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.drafts.isNotEmpty() && state.viewMode == ImportViewMode.LIST) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!state.isSaving) {
                            viewModel.processAction(ImportAction.ConfirmSelected)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(
                            text = if (state.isSaving) {
                                stringResource(
                                    R.string.import_saving_progress,
                                    state.savedCount,
                                    state.saveTotal,
                                )
                            } else {
                                stringResource(
                                    R.string.import_fab_format,
                                    state.validSelectedCount,
                                )
                            },
                        )
                    },
                    expanded = !state.isSaving,
                )
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .padding(if (isCompact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
            ) {
                if (state.drafts.isEmpty()) {
                    if (state.isLoading) {
                        ParsingIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        EmptyImportState(onPickPdf = { pdfPicker.launch("application/pdf") })
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.weight(1f),
                        ) {
                            listOf(
                                ImportViewMode.CARD to R.string.card_view,
                                ImportViewMode.LIST to R.string.list_view,
                            ).forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = state.viewMode == option.first,
                                    onClick = {
                                        viewModel.processAction(
                                            if (option.first == ImportViewMode.CARD) {
                                                ImportAction.SwitchToCard
                                            } else {
                                                ImportAction.SwitchToList
                                            },
                                        )
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = 2,
                                    ),
                                    label = {
                                        Text(
                                            text = stringResource(option.second),
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                        Text(
                            text = reviewedProgressText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.reviewProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.small),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )

                    val summaryText = buildString {
                        append(
                            stringResource(
                                R.string.import_summary_compact,
                                state.readyCount,
                                state.unavailableCount,
                                state.acceptedCount,
                                state.rejectedCount,
                            ),
                        )
                        if (state.duplicateCount > 0) {
                            append(" • ")
                            append(stringResource(R.string.import_duplicate_summary, state.duplicateCount))
                        }
                    }

                    Text(
                        text = summaryText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )

                    if (!state.duplicateCheckAvailable) {
                        Text(
                            text = stringResource(R.string.import_duplicate_check_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    when (state.viewMode) {
                        ImportViewMode.CARD -> {
                            ImportCardStack(
                                state = state,
                                onAction = viewModel::processAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }

                        ImportViewMode.LIST -> {
                            ImportListView(
                                state = state,
                                onAction = viewModel::processAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }
                }
            }

            if (state.isLoading && state.drafts.isNotEmpty()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card {
                        ParsingIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyImportState(onPickPdf: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Upload,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.import_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPickPdf,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(text = stringResource(R.string.pick_pdf))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = stringResource(R.string.import_privacy_note),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ParsingIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.parsing_statement),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.parsing_on_device_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
