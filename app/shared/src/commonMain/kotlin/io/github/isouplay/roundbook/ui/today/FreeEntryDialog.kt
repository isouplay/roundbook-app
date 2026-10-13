package io.github.isouplay.roundbook.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.domain.entry.BookProblem
import io.github.isouplay.roundbook.domain.entry.DrinkChoice
import io.github.isouplay.roundbook.ui.parseDecimal
import io.github.isouplay.roundbook.ui.parsePriceCents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.cancel
import roundbook.app.shared.generated.resources.free_abv
import roundbook.app.shared.generated.resources.free_add
import roundbook.app.shared.generated.resources.free_amount
import roundbook.app.shared.generated.resources.free_change
import roundbook.app.shared.generated.resources.free_counts
import roundbook.app.shared.generated.resources.free_entry
import roundbook.app.shared.generated.resources.free_entry_instead
import roundbook.app.shared.generated.resources.free_invalid_abv
import roundbook.app.shared.generated.resources.free_invalid_amount
import roundbook.app.shared.generated.resources.free_invalid_price
import roundbook.app.shared.generated.resources.free_name
import roundbook.app.shared.generated.resources.free_price

/**
 * Free entry without menu: name, amount (cl), strength, price, counts as drink. [onSave] books (or changes)
 * and returns the problem if the entry was rejected; then the dialog stays open and says why.
 */
@Composable
fun FreeEntryDialog(
    instead: Boolean,
    onDismiss: () -> Unit,
    onSave: suspend (DrinkChoice.Free) -> BookProblem?,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var abv by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var counts by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (instead) Res.string.free_entry_instead else Res.string.free_entry)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.free_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(Res.string.free_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = abv,
                    onValueChange = { abv = it },
                    label = { Text(stringResource(Res.string.free_abv)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(Res.string.free_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = counts, onCheckedChange = { counts = it })
                    Text(stringResource(Res.string.free_counts))
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val cents = parsePriceCents(price)
                    val cl = if (amount.isBlank()) null else parseDecimal(amount)
                    val strength = if (abv.isBlank()) null else parseDecimal(abv)
                    problem = when {
                        cents == null -> getString(Res.string.free_invalid_price)
                        amount.isNotBlank() && cl == null -> getString(Res.string.free_invalid_amount)
                        abv.isNotBlank() && strength == null -> getString(Res.string.free_invalid_abv)
                        else -> {
                            val choice = DrinkChoice.Free(name, cl?.let { it / 100.0 }, strength, cents, counts)
                            onSave(choice)?.let { bookProblemText(it) }
                        }
                    }
                    if (problem == null) onDismiss()
                }
            }) {
                Text(stringResource(if (instead) Res.string.free_change else Res.string.free_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
