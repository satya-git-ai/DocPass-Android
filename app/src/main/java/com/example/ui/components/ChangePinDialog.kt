package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.PasswordPolicy
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.Navy950

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onChangePin: (oldPin: String, newPin: String, (Boolean, String?) -> Unit) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val policyResult = remember(newPin) {
        PasswordPolicy.validate(newPin)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Master Password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter your current password and choose a new strong Master Password to re-encrypt your key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = oldPin,
                    onValueChange = {
                        oldPin = it
                        errorMessage = null
                    },
                    label = { Text("Current Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_old_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPrimary)
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        newPin = it
                        errorMessage = null
                    },
                    label = { Text("New Master Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_new_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPrimary)
                )

                // Live Password Policy Requirements
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Password Requirements:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PasswordRequirementItem(label = "At least 8 characters", isMet = policyResult.hasMinLength)
                    PasswordRequirementItem(label = "1 uppercase letter (A–Z)", isMet = policyResult.hasUppercase)
                    PasswordRequirementItem(label = "1 lowercase letter (a–z)", isMet = policyResult.hasLowercase)
                    PasswordRequirementItem(label = "1 number (0–9)", isMet = policyResult.hasDigit)
                    PasswordRequirementItem(label = "1 special character (! @ # $ % ^ & *)", isMet = policyResult.hasSpecialChar)
                    PasswordRequirementItem(
                        label = if (policyResult.detectedCommonPattern != null)
                            "No common passwords (rejects \"${policyResult.detectedCommonPattern}\")"
                        else
                            "No easily guessable passwords (e.g. Password123!, Admin@123)",
                        isMet = policyResult.notCommon
                    )
                }

                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = {
                        confirmNewPin = it
                        errorMessage = null
                    },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_confirm_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPrimary)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (oldPin.isBlank()) {
                        errorMessage = "Enter your current password"
                        return@Button
                    }
                    if (!policyResult.isValid) {
                        errorMessage = policyResult.errors.firstOrNull() ?: "Password does not meet strict security requirements"
                        return@Button
                    }
                    if (newPin != confirmNewPin) {
                        errorMessage = "New password confirmation does not match"
                        return@Button
                    }

                    isSubmitting = true
                    onChangePin(oldPin, newPin) { success, err ->
                        isSubmitting = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = err ?: "Incorrect current password"
                        }
                    }
                },
                enabled = !isSubmitting && policyResult.isValid && confirmNewPin.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Navy950
                ),
                modifier = Modifier.testTag("change_pin_submit_button")
            ) {
                Text(text = if (isSubmitting) "Updating..." else "Update Password", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("change_pin_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun PasswordRequirementItem(label: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isMet) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isMet) EmeraldSecurity else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (isMet) EmeraldSecurity else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
