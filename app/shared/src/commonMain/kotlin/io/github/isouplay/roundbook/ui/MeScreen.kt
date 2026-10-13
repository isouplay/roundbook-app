package io.github.isouplay.roundbook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import roundbook.app.shared.generated.resources.Res
import roundbook.app.shared.generated.resources.me_places
import roundbook.app.shared.generated.resources.me_title

/** Ich: what belongs to me. In package 3 only the places (from package 2). */
@Composable
fun MeScreen(onPlaces: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.me_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = onPlaces) { Text(stringResource(Res.string.me_places)) }
    }
}
