package ai.baseweight.sideeye.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.baseweight.sideeye.data.security.AuthMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSettingsScreen(
    viewModel: VaultViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showChangePinDialog by remember { mutableStateOf(false) }
    var authRequiredOnLaunch by remember { mutableStateOf(viewModel.isAuthRequiredOnLaunch()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Authentication Method Section
            Text(
                text = "Authentication Method",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Biometric option
                if (viewModel.isBiometricAvailable()) {
                    ListItem(
                        headlineContent = { Text("Biometric") },
                        supportingContent = { Text("Use fingerprint or face recognition") },
                        leadingContent = {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        },
                        trailingContent = {
                            RadioButton(
                                selected = uiState.authMethod == AuthMethod.BIOMETRIC,
                                onClick = { viewModel.setAuthMethod(AuthMethod.BIOMETRIC) }
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.setAuthMethod(AuthMethod.BIOMETRIC)
                        }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // PIN option
                ListItem(
                    headlineContent = { Text("PIN") },
                    supportingContent = { Text("Use a 4-6 digit PIN") },
                    leadingContent = {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                    },
                    trailingContent = {
                        RadioButton(
                            selected = uiState.authMethod == AuthMethod.PIN,
                            onClick = { viewModel.setAuthMethod(AuthMethod.PIN) }
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setAuthMethod(AuthMethod.PIN)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PIN Management Section
            Text(
                text = "PIN Management",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Change PIN") },
                    supportingContent = { Text("Update your vault PIN") },
                    modifier = Modifier.clickable { showChangePinDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Section
            Text(
                text = "Security",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Require auth on app launch") },
                    supportingContent = { Text("Lock vault when app restarts") },
                    trailingContent = {
                        Switch(
                            checked = authRequiredOnLaunch,
                            onCheckedChange = {
                                authRequiredOnLaunch = it
                                viewModel.setAuthRequiredOnLaunch(it)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info text
            Text(
                text = "Vault automatically locks after 15 minutes of inactivity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    // Change PIN dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onConfirm = { oldPin, newPin ->
                val success = viewModel.changePin(oldPin, newPin)
                if (success) {
                    showChangePinDialog = false
                }
                success
            }
        )
    }
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (oldPin: String, newPin: String) -> Boolean
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) currentPin = it },
                    label = { Text("Current PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New PIN (4-6 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        currentPin.length < 4 -> error = "Enter your current PIN"
                        newPin.length < 4 -> error = "New PIN must be 4-6 digits"
                        newPin != confirmPin -> error = "New PINs don't match"
                        else -> {
                            if (!onConfirm(currentPin, newPin)) {
                                error = "Current PIN is incorrect"
                                currentPin = ""
                            }
                        }
                    }
                }
            ) {
                Text("Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
