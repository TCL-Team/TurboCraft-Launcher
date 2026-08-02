package com.movtery.zalithlauncher.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.game.marketplace.MarketplaceApi
import com.movtery.zalithlauncher.game.marketplace.SetupData
import kotlinx.coroutines.launch

@Composable
fun SetupMarketplaceScreen(
    modifier: Modifier = Modifier,
    showToast: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var setups by remember { mutableStateOf<List<SetupData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showPublishDialog by remember { mutableStateOf(false) }

    fun refresh() {
        coroutineScope.launch {
            loading = true
            runCatching { MarketplaceApi.fetchSetups() }
                .onSuccess { setups = it }
                .onFailure { showToast("Setup list load nahi ho paayi: ${it.message}") }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Setup Marketplace", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showPublishDialog = true }) {
                Text(text = "Publish Setup")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (setups.isEmpty()) {
                Text(
                    text = "Abhi tak koi setup publish nahi hua. Sabse pehle tum karo!",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(setups) { setup ->
                        SetupCard(
                            setup = setup,
                            onInstall = {
                                coroutineScope.launch {
                                    runCatching { MarketplaceApi.installSetup(setup) }
                                        .onSuccess { showToast("${setup.title} install ho gaya!") }
                                        .onFailure { showToast("Install fail hua: ${it.message}") }
                                    refresh()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPublishDialog) {
        PublishSetupDialog(
            onDismiss = { showPublishDialog = false },
            onPublish = { title, author, fps ->
                coroutineScope.launch {
                    runCatching { MarketplaceApi.publishCurrentSetup(title, author, fps) }
                        .onSuccess { showToast("Setup publish ho gaya!") }
                        .onFailure { showToast("Publish fail hua: ${it.message}") }
                    showPublishDialog = false
                    refresh()
                }
            }
        )
    }
}

@Composable
private fun SetupCard(
    setup: SetupData,
    onInstall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = setup.title, style = MaterialTheme.typography.titleMedium)
                Text(text = "by ${setup.author}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "FPS: ${setup.fps}  •  Downloads: ${setup.downloads}  •  ★ ${"%.1f".format(setup.rating)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onInstall) {
                Text(text = "Install")
            }
        }
    }
}

@Composable
private fun PublishSetupDialog(
    onDismiss: () -> Unit,
    onPublish: (title: String, author: String, fps: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var fps by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Apna Setup Publish Karo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Setup ka naam (jaise: PvP Setup)") })
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Tumhara naam") })
                OutlinedTextField(value = fps, onValueChange = { fps = it }, label = { Text("Average FPS") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fpsValue = fps.toIntOrNull() ?: 0
                    if (title.isNotBlank() && author.isNotBlank()) {
                        onPublish(title, author, fpsValue)
                    }
                }
            ) {
                Text(text = "Publish")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(text = "Cancel") }
        }
    )
}
