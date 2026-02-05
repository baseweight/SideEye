package ai.baseweight.sideeye.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun VaultSetupScreen(
    onSetupComplete: (pin: String) -> Unit,
    onSkip: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isValid = pin.length >= 4 && pin == confirmPin

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set Up Vault PIN",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create a PIN to protect your private vault. Photos you swipe up will be encrypted and stored securely.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    pin = it
                    errorMessage = null
                }
            },
            label = { Text("PIN (4-6 digits)") },
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(
                        imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPin) "Hide PIN" else "Show PIN"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = confirmPin,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    confirmPin = it
                    errorMessage = null
                }
            },
            label = { Text("Confirm PIN") },
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = confirmPin.isNotEmpty() && pin != confirmPin,
            supportingText = if (confirmPin.isNotEmpty() && pin != confirmPin) {
                { Text("PINs don't match") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (pin.length < 4) {
                    errorMessage = "PIN must be at least 4 digits"
                } else if (pin != confirmPin) {
                    errorMessage = "PINs don't match"
                } else {
                    onSetupComplete(pin)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = isValid
        ) {
            Text("Set PIN")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now")
        }
    }
}
