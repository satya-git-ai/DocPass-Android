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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.data.PasswordEntity
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
    var accountNumber by remember { mutableStateOf(passwordToEdit?.accountIdentifier ?: "") }
    var password by remember { mutableStateOf(initialPlainPassword) }
    var notes by remember { mutableStateOf(initialDecryptedNotes) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isBank = category.equals("Bank", ignoreCase = true)
    val isAtm = category.equals("ATM", ignoreCase = true)
    val isEmail = category.equals("Email/Gmail", ignoreCase = true) || category.equals("Email", ignoreCase = true) || category.equals("Gmail", ignoreCase = true)
    val categories = listOf("Bank", "Email/Gmail", "ATM", "Education", "Social", "Shopping", "Other")

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
                // Title / Bank Name / App Name (Hidden for Email/Gmail)
                if (!isEmail) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            errorMessage = null
                        },
                        label = { Text(if (isBank || isAtm) "Bank name *" else "App / website / service *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity
                        )
                    )
                }

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

                if (isBank) {
                    // Bank Category: Account number field
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = {
                            accountNumber = it
                            errorMessage = null
                        },
                        label = { Text("Account number *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_account_number_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity
                        )
                    )

                    // Bank Category: Username field (only display if category is bank)
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_username_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity
                        )
                    )
                } else if (isAtm) {
                    // ATM: Card number
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text("Card number *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_username_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity
                        )
                    )
                } else if (isEmail) {
                    // Email/Gmail: Username / Login ID
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text("Username / Login ID *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pwd_form_username_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldSecurity
                        )
                    )
                } else {
                    // Non-Bank / Non-ATM: Username / Email / Login ID
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
                }

                // Password / PIN
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(if (isAtm) "PIN *" else "Password / PIN *") },
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

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
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
                    if (!isEmail && title.isBlank()) {
                        errorMessage = if (isBank || isAtm) "Bank name is required" else "App / website name is required"
                        return@Button
                    }
                    if (isBank) {
                        if (accountNumber.isBlank()) {
                            errorMessage = "Account number is required"
                            return@Button
                        }
                    } else if (isAtm) {
                        if (username.isBlank()) {
                            errorMessage = "Card number is required"
                            return@Button
                        }
                    } else {
                        if (username.isBlank()) {
                            errorMessage = "Username or Login ID is required"
                            return@Button
                        }
                    }
                    if (password.isBlank()) {
                        errorMessage = if (isAtm) "PIN is required" else "Password is required"
                        return@Button
                    }

                    val finalTitle = if (isEmail) {
                        if (title.isNotBlank()) title else "Email / Gmail"
                    } else {
                        title
                    }
                    val finalUsername = if (isBank) {
                        if (username.isNotBlank()) username else accountNumber
                    } else {
                        username
                    }
                    val finalAccountId = if (isBank) accountNumber else ""

                    onSave(finalTitle, category, finalUsername, finalAccountId, password, notes)
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
