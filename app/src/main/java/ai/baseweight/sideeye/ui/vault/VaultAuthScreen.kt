package ai.baseweight.sideeye.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultAuthScreen(
    isSetupMode: Boolean,
    isBiometricAvailable: Boolean,
    onPinSubmit: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onBackClick: () -> Unit,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStep by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val maxPinLength = 6
    val minPinLength = 4

    // Clear local error when external error changes
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            pin = ""
            confirmPin = ""
            if (isSetupMode) isConfirmStep = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSetupMode) "Set Up PIN" else "Enter PIN"
                    )
                },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Instruction text
            Text(
                text = when {
                    isSetupMode && !isConfirmStep -> "Create a 4-6 digit PIN"
                    isSetupMode && isConfirmStep -> "Confirm your PIN"
                    else -> "Enter your PIN to unlock"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN dots display
            PinDotsDisplay(
                pinLength = if (isConfirmStep) confirmPin.length else pin.length,
                maxLength = maxPinLength
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            val displayError = localError ?: errorMessage
            if (displayError != null) {
                Text(
                    text = displayError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Numeric keypad
            NumericKeypad(
                onNumberClick = { number ->
                    localError = null
                    if (isConfirmStep) {
                        if (confirmPin.length < maxPinLength) {
                            confirmPin += number
                        }
                    } else {
                        if (pin.length < maxPinLength) {
                            pin += number
                        }
                    }
                },
                onDeleteClick = {
                    if (isConfirmStep) {
                        if (confirmPin.isNotEmpty()) {
                            confirmPin = confirmPin.dropLast(1)
                        }
                    } else {
                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                        }
                    }
                },
                onBiometricClick = if (!isSetupMode && isBiometricAvailable) onBiometricClick else null
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Submit button
            val currentPin = if (isConfirmStep) confirmPin else pin
            val canSubmit = currentPin.length >= minPinLength

            if (isSetupMode) {
                if (isConfirmStep) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isConfirmStep = false
                                confirmPin = ""
                                localError = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                if (pin == confirmPin) {
                                    onPinSubmit(pin)
                                } else {
                                    localError = "PINs don't match. Try again."
                                    confirmPin = ""
                                }
                            },
                            enabled = canSubmit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Confirm")
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            isConfirmStep = true
                        },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue")
                    }
                }
            } else {
                Button(
                    onClick = { onPinSubmit(pin) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PinDotsDisplay(
    pinLength: Int,
    maxLength: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLength) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < pinLength) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: (() -> Unit)?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: 1, 2, 3
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KeypadButton("1") { onNumberClick("1") }
            KeypadButton("2") { onNumberClick("2") }
            KeypadButton("3") { onNumberClick("3") }
        }
        // Row 2: 4, 5, 6
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KeypadButton("4") { onNumberClick("4") }
            KeypadButton("5") { onNumberClick("5") }
            KeypadButton("6") { onNumberClick("6") }
        }
        // Row 3: 7, 8, 9
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            KeypadButton("7") { onNumberClick("7") }
            KeypadButton("8") { onNumberClick("8") }
            KeypadButton("9") { onNumberClick("9") }
        }
        // Row 4: Biometric/Empty, 0, Delete
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onBiometricClick != null) {
                KeypadIconButton(
                    icon = {
                        Icon(
                            Icons.Filled.Fingerprint,
                            contentDescription = "Use Biometric",
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    onClick = onBiometricClick
                )
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }
            KeypadButton("0") { onNumberClick("0") }
            KeypadIconButton(
                icon = {
                    Text(
                        text = "\u232B",
                        fontSize = 28.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeypadIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
