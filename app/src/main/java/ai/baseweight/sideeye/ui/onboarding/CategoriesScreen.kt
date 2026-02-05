package ai.baseweight.sideeye.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.baseweight.sideeye.data.ai.FlagCategory

@Composable
fun CategoriesScreen(
    enabledCategories: Set<FlagCategory>,
    onToggleCategory: (FlagCategory) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Choose Categories",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select which types of content SideEye should flag during scans. You can change these later in settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                FlagCategory.entries.forEachIndexed { index, category ->
                    CategoryToggleItem(
                        category = category,
                        isEnabled = category in enabledCategories,
                        onToggle = { onToggleCategory(category) }
                    )
                    if (index < FlagCategory.entries.size - 1) {
                        Divider()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (enabledCategories.isEmpty()) {
            Text(
                text = "Select at least one category to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = enabledCategories.isNotEmpty()
        ) {
            Text("Start Using SideEye")
        }
    }
}

@Composable
private fun CategoryToggleItem(
    category: FlagCategory,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() }
        )
    }
}
