package com.naveenapps.expensemanager.feature.transaction.import.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naveenapps.expensemanager.core.designsystem.ui.components.AppCardView
import com.naveenapps.expensemanager.feature.transaction.R

@Composable
fun ImportListView(
    state: ImportState,
    onAction: (ImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(ImportAction.SelectAll) }) {
                Text(stringResource(R.string.import_select_all_ready))
            }
            TextButton(onClick = { onAction(ImportAction.DeselectAll) }) {
                Text(stringResource(R.string.import_clear_selection))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.drafts, key = { it.parsed.id }) { draft ->
                AppCardView(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (draft.isImportable) {
                        { onAction(ImportAction.ToggleSelection(draft.parsed.id)) }
                    } else {
                        null
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = draft.isSelected && draft.isImportable,
                            onCheckedChange = {
                                if (draft.isImportable) {
                                    onAction(ImportAction.ToggleSelection(draft.parsed.id))
                                }
                            },
                            enabled = draft.isImportable,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (draft.transactionType.name == "INCOME") "+" else "−"}${draft.amountText} • ${draft.parsed.counterpartyName}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = draft.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                            if (!draft.parsed.isSuccess) {
                                ImportStatusLabel(
                                    text = stringResource(R.string.import_bank_failed_title),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = stringResource(R.string.import_bank_failed_detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else if (!draft.isImportable) {
                                ImportStatusLabel(
                                    text = stringResource(R.string.import_unavailable),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = stringResource(R.string.import_unavailable_detail, draft.parsed.status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else if (draft.isDuplicate) {
                                ImportStatusLabel(
                                    text = stringResource(R.string.import_possible_duplicate),
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.import_duplicate_detail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStatusLabel(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
