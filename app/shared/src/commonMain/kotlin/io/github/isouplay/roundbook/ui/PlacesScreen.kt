package io.github.isouplay.roundbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isouplay.roundbook.domain.PlaceId
import io.github.isouplay.roundbook.domain.Roundbook
import io.github.isouplay.roundbook.domain.place.PlaceNameProblem
import io.github.isouplay.roundbook.domain.place.PlaceResult
import io.github.isouplay.roundbook.domain.place.Places
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.place_create
import roundbook.app.shared.generated.resources.place_name_empty
import roundbook.app.shared.generated.resources.place_name_label
import roundbook.app.shared.generated.resources.place_name_too_long
import roundbook.app.shared.generated.resources.places_empty
import roundbook.app.shared.generated.resources.places_title

/** Screen 1: list of places, create a place by name. */
@Composable
fun PlacesScreen(roundbook: Roundbook, onOpenPlace: (PlaceId) -> Unit) {
    var places by remember { mutableStateOf(roundbook.places.all()) }
    var name by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<PlaceNameProblem?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.places_title), style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                problem = null
            },
            label = { Text(stringResource(Res.string.place_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            when (val result = roundbook.places.create(name)) {
                is PlaceResult.Done -> {
                    name = ""
                    places = roundbook.places.all()
                    onOpenPlace(result.place.id)
                }
                is PlaceResult.Rejected -> problem = result.problem
                PlaceResult.UnknownPlace -> Unit
            }
        }) {
            Text(stringResource(Res.string.place_create))
        }
        when (problem) {
            PlaceNameProblem.EMPTY -> Text(stringResource(Res.string.place_name_empty))
            PlaceNameProblem.TOO_LONG -> Text(stringResource(Res.string.place_name_too_long, Places.NAME_MAX.toString()))
            null -> Unit
        }

        Spacer(Modifier.height(8.dp))
        if (places.isEmpty()) {
            Text(stringResource(Res.string.places_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(places, key = { it.id.value }) { place ->
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlace(place.id) }
                            .padding(vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
