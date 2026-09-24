package com.naveenapps.expensemanager.feature.transaction.import.review

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Show the parsing indicator immediately: extraction + PDFBox
            // work below can take several seconds with no other signal.
            viewModel.processAction(ImportAction.StartParsing)
            scope.launch {
                // I/O + PDFBox work stays off the main thread.
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

    LaunchedEffect(state.lastActionLabel, state.currentIndex) {
        state.lastActionLabel?.let { label ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "$label ${state.progressText}",
                    actionLabel = if (state.canUndo) "Undo" else null,
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.processAction(ImportAction.UndoLast)
                }
            }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpenseManagerTopAppBar(
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationBackClick = { viewModel.processAction(ImportAction.ClosePage) },
                title = stringResource(R.string.import_pdf_title),
            )
        },
        floatingActionButton = {
            if (state.drafts.isNotEmpty()) {
                val selectedCount = state.drafts.count { it.isSelected }
                ExtendedFloatingActionButton(
                    onClick = { viewModel.processAction(ImportAction.ConfirmSelected) },
                ) {
                    Text(
                        text = if (state.isSaving) {
                            "Saving ${state.savedCount}/${state.saveTotal}…"
                        } else {
                            stringResource(R.string.import_fab_format, selectedCount)
                        },
                    )
                }
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
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.drafts.isEmpty()) {
                    if (state.isLoading) {
                        ParsingIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.import_pdf_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = { pdfPicker.launch("application/pdf") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.pick_pdf))
                        }
                        Text(
                            text = stringResource(R.string.no_parsed_transactions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.viewMode == ImportViewMode.CARD,
                        onClick = { viewModel.processAction(ImportAction.SwitchToCard) },
                        label = { Text(stringResource(R.string.card_view)) },
                    )
                    FilterChip(
                        selected = state.viewMode == ImportViewMode.LIST,
                        onClick = { viewModel.processAction(ImportAction.SwitchToList) },
                        label = { Text(stringResource(R.string.list_view)) },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = state.progressText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                val skippedCount = state.drafts.count { !it.isImportable }
                if (skippedCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.importable_status,
                            state.drafts.size - skippedCount,
                            skippedCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }

                OutlinedButton(
                    onClick = { pdfPicker.launch("application/pdf") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.pick_pdf))
                }

                if (!state.duplicateCheckAvailable) {
                    Text(
                        text = "Duplicate check unavailable — please review carefully.",
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

                Spacer(modifier = Modifier.height(72.dp))
            }
            }

            // Modal parsing overlay for re-picks while drafts are shown.
            if (state.isLoading && state.drafts.isNotEmpty()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Card {
                        ParsingIndicator(modifier = Modifier.padding(24.dp))
                    }
                }
            }
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
