package com.movtery.zalithlauncher.ui.screens.content.elements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.game.cloudsync.CloudAuthApi
import com.movtery.zalithlauncher.game.cloudsync.CloudAuthResult
import com.movtery.zalithlauncher.setting.AllSettings
import kotlinx.coroutines.launch

/**
 * Cloud Sync - TCL Cloud account login/signup.
 * Ye Minecraft account se bilkul alag account hai - iska kaam sirf
 * mods, settings, aur worlds ko devices ke beech sync karna hai.
 */
@Composable
fun CloudSyncDialog(
    onDismissRequest: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val loggedInEmail = AllSettings.cloudSyncEmail.state

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleResult(result: CloudAuthResult) {
        loading = false
        when (result) {
            is CloudAuthResult.Success -> {
                errorMessage = null
                onDismissRequest()
            }
            is CloudAuthResult.Failure -> {
                errorMessage = result.reason
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Cloud Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Corner-style explanation, English mein, taaki koi bhi samajh jaaye
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        text = "TCL Cloud is separate from your Minecraft account — " +
                                "it syncs your mods, settings & worlds across devices.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (loggedInEmail.isNotBlank()) {
                    Text(text = "Logged in as: $loggedInEmail")
                    Button(
                        onClick = {
                            CloudAuthApi.signOut()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Log Out")
                    }
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )

                    errorMessage?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }

                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (loggedInEmail.isBlank()) {
                TextButton(
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true
                        errorMessage = null
                        coroutineScope.launch {
                            handleResult(CloudAuthApi.signIn(email, password))
                        }
                    }
                ) {
                    Text(text = "Log In")
                }
            }
        },
        dismissButton = {
            if (loggedInEmail.isBlank()) {
                TextButton(
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true
                        errorMessage = null
                        coroutineScope.launch {
                            handleResult(CloudAuthApi.signUp(email, password))
                        }
                    }
                ) {
                    Text(text = "Sign Up")
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text(text = "Close")
                }
            }
        }
    )
}
