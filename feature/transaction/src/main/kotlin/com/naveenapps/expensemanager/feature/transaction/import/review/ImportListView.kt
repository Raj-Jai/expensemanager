package com.naveenapps.expensemanager.feature.transaction.import.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(ImportAction.SelectAll) }) {
                Text(stringResource(R.string.select_all))
            }
            TextButton(onClick = { onAction(ImportAction.DeselectAll) }) {
                Text(stringResource(R.string.deselect_all))
            }
            FilterChip(
                selected = false,
                onClick = { onAction(ImportAction.ConfirmSelected) },
                label = { Text("${stringResource(R.string.import_selected)} (${state.drafts.count { it.isSelected }})") },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.drafts, key = { it.parsed.id }) { draft ->
                AppCardView(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = draft.isSelected,
                            onCheckedChange = { onAction(ImportAction.ToggleSelection(draft.parsed.id)) },
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
                            if (draft.isDuplicate) {
                                Text(
                                    text = stringResource(R.string.already_exists),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (!draft.parsed.isSuccess) {
                                Text(
                                    text = "Status: ${draft.parsed.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
