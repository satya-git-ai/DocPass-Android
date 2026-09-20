package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PasswordEntity
import com.example.security.PasswordGenerator
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.Navy950

@Composable
fun AddEditPasswordDialog(
    passwordToEdit: PasswordEntity? = null,
    initialCategory: String = "Bank",
    initialPlainPassword: String = "",
    initialDecryptedNotes: String = "",
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: String,
        username: String,
        accountIdentifier: String,
        plainPassword: String,
        notes: String
    ) -> Unit
) {
    var title by remember { mutableStateOf(passwordToEdit?.title ?: "") }
    var category by remember { mutableStateOf(passwordToEdit?.category ?: initialCategory) }
    var username by remember { mutableStateOf(passwordToEdit?.username ?: "") }
    var accountIdentifier by remember { mutableStateOf(passwordToEdit?.accountIdentifier ?: "") }
    var password by remember { mutableStateOf(initialPlainPassword) }
    var notes by remember { mutableStateOf(initialDecryptedNotes) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Bank", "ATM", "Education", "Social", "Shopping", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (passwordToEdit == null) "Add Password Entry" else "Edit Password Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title / App Name
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("App / Website / Service *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pwd_form_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldSecurity
                    )
                )

                // Category Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = EmeraldSecurity)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_category_dropdown"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Clickable overlay over text field to ensure click is captured reliably
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { categoryMenuExpanded = true }
                            .testTag("pwd_form_category_overlay")
                    )

                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                trailingIcon = {
                                    if (cat == category) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = EmeraldSecurity)
                                    }
                                },
                                onClick = {
                                    category = cat
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Username / Email
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Username / Email / Login ID *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pwd_form_username_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldSecurity
                    )
                )

                // Account Identifier
                OutlinedTextField(
                    value = accountIdentifier,
                    onValueChange = { accountIdentifier = it },
                    label = { Text("Account Identifier / Card # (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pwd_form_account_id_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldSecurity
                    )
                )

                // Password with inline generator trigger
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password / PIN *") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = {
                                password = PasswordGenerator.generateMemorable()
                                isPasswordVisible = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("pwd_form_generate_button")
                        ) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate", fontSize = 12.sp)
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Encrypted Notes (Optional)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pwd_form_notes_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldSecurity
                    )
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
                    if (title.isBlank()) {
                        errorMessage = "App / Website name is required"
                        return@Button
                    }
                    if (username.isBlank()) {
                        errorMessage = "Username or Login ID is required"
                        return@Button
                    }
                    if (password.isBlank()) {
                        errorMessage = "Password is required"
                        return@Button
                    }
                    onSave(title, category, username, accountIdentifier, password, notes)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldSecurity,
                    contentColor = Navy950
                ),
                modifier = Modifier.testTag("pwd_form_save_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("pwd_form_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    )
}
