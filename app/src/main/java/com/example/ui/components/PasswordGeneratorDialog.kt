package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.PasswordGenerator
import com.example.ui.theme.AmberSecurity
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.Navy950
import com.example.ui.theme.RoseSecurity

@Composable
fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onCopyPassword: (String) -> Unit
) {
    var isMemorableMode by remember { mutableStateOf(true) }
    var length by remember { mutableFloatStateOf(16f) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }

    fun generatePwd(): String {
        return if (isMemorableMode) {
            PasswordGenerator.generateMemorable()
        } else {
            PasswordGenerator.generate(
                length = length.toInt(),
                useUppercase = useUppercase,
                useLowercase = useLowercase,
                useDigits = useDigits,
                useSymbols = useSymbols,
                avoidAmbiguous = avoidAmbiguous
            )
        }
    }

    var generatedPassword by remember { mutableStateOf(generatePwd()) }

    fun refreshPassword() {
        generatedPassword = generatePwd()
    }

    val strength: PasswordGenerator.Strength = remember(generatedPassword) {
        PasswordGenerator.estimateStrength(generatedPassword)
    }

    val (strengthColor, progress) = when (strength) {
        PasswordGenerator.Strength.VERY_STRONG -> EmeraldSecurity to 1.0f
        PasswordGenerator.Strength.STRONG -> EmeraldSecurity to 0.75f
        PasswordGenerator.Strength.MEDIUM -> AmberSecurity to 0.5f
        PasswordGenerator.Strength.WEAK, PasswordGenerator.Strength.VERY_WEAK -> RoseSecurity to 0.25f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = CyanPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Password Generator",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Generated Password Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = generatedPassword,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            Row {
                                IconButton(
                                    onClick = { refreshPassword() },
                                    modifier = Modifier.size(36.dp).testTag("generator_refresh_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Autorenew,
                                        contentDescription = "Regenerate",
                                        tint = CyanPrimary
                                    )
                                }

                                IconButton(
                                    onClick = { onCopyPassword(generatedPassword) },
                                    modifier = Modifier.size(36.dp).testTag("generator_copy_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy Password",
                                        tint = EmeraldSecurity
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Strength indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Strength: ${strength.label}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = strengthColor
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = strengthColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                // Mode Switcher: Memorable vs Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMemorableMode) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                            .clickable {
                                isMemorableMode = true
                                generatedPassword = PasswordGenerator.generateMemorable()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Memorable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isMemorableMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isMemorableMode) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isMemorableMode) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                            .clickable {
                                isMemorableMode = false
                                refreshPassword()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Custom Options",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (!isMemorableMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isMemorableMode) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isMemorableMode) {
                    Text(
                        text = "Generates easy-to-type passwords like Apple@33, meeting all security requirements (letters, numbers, symbols).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                } else {
                    // Length Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Length", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${length.toInt()} characters",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        }
                        Slider(
                            value = length,
                            onValueChange = {
                                length = it
                                refreshPassword()
                            },
                            valueRange = 8f..32f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanPrimary,
                                activeTrackColor = CyanPrimary
                            ),
                            modifier = Modifier.testTag("generator_length_slider")
                        )
                    }

                    // Character Type Toggles
                    GeneratorOptionRow("Uppercase (A-Z)", useUppercase) {
                        useUppercase = it
                        refreshPassword()
                    }
                    GeneratorOptionRow("Lowercase (a-z)", useLowercase) {
                        useLowercase = it
                        refreshPassword()
                    }
                    GeneratorOptionRow("Digits (0-9)", useDigits) {
                        useDigits = it
                        refreshPassword()
                    }
                    GeneratorOptionRow("Symbols (!@#$%)", useSymbols) {
                        useSymbols = it
                        refreshPassword()
                    }
                    GeneratorOptionRow("Avoid Ambiguous (l, 1, I, O, 0)", avoidAmbiguous) {
                        avoidAmbiguous = it
                        refreshPassword()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCopyPassword(generatedPassword) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Navy950
                ),
                modifier = Modifier.testTag("generator_use_copy_button")
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy & Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun GeneratorOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = CyanPrimary,
                checkmarkColor = Navy950
            )
        )
    }
}

