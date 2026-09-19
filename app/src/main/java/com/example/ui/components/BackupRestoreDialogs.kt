package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.security.BackupEngine
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.Navy950

@Composable
fun ExportBackupDialog(
    onDismiss: () -> Unit,
    onPerformExport: (Uri, String, (BackupEngine.BackupResult) -> Unit) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            isExporting = true
            onPerformExport(uri, passphrase) { result ->
                isExporting = false
                if (result.success) {
                    onDismiss()
                } else {
                    errorMessage = result.errorMessage ?: "Export failed"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Upload, contentDescription = null, tint = CyanPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Encrypted Backup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Protect your backup with a custom passphrase. All documents and passwords will be re-encrypted using AES-256-GCM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorMessage = null
                    },
                    label = { Text("Backup Passphrase (min 6 chars)") },
                    singleLine = true,
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_backup_passphrase_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPrimary)
                )

                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = {
                        confirmPassphrase = it
                        errorMessage = null
                    },
                    label = { Text("Confirm Backup Passphrase") },
                    singleLine = true,
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_backup_confirm_passphrase_input"),
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
                    if (passphrase.length < 6) {
                        errorMessage = "Passphrase must be at least 6 characters"
                        return@Button
                    }
                    if (passphrase != confirmPassphrase) {
                        errorMessage = "Passphrases do not match"
                        return@Button
                    }
                    val timestamp = System.currentTimeMillis()
                    createDocumentLauncher.launch("docpass_backup_$timestamp.enc")
                },
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Navy950
                ),
                modifier = Modifier.testTag("export_backup_save_button")
            ) {
                Text(text = if (isExporting) "Exporting..." else "Choose Location & Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_backup_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun ImportBackupDialog(
    onDismiss: () -> Unit,
    onPerformImport: (Uri, String, (BackupEngine.BackupResult) -> Unit) -> Unit
) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = CyanPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Import Encrypted Backup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select your .enc DocPass backup file and enter the passphrase used during export.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_backup_select_file_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedFileUri != null) "Backup File Selected ✓" else "Choose Backup File (.enc)",
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        errorMessage = null
                    },
                    label = { Text("Backup Passphrase") },
                    singleLine = true,
                    visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                            Icon(
                                imageVector = if (isPassphraseVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_backup_passphrase_input"),
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
                    if (selectedFileUri == null) {
                        errorMessage = "Please select a backup file"
                        return@Button
                    }
                    if (passphrase.isBlank()) {
                        errorMessage = "Please enter the backup passphrase"
                        return@Button
                    }
                    isImporting = true
                    onPerformImport(selectedFileUri!!, passphrase) { result ->
                        isImporting = false
                        if (result.success) {
                            onDismiss()
                        } else {
                            errorMessage = result.errorMessage ?: "Failed to import backup"
                        }
                    }
                },
                enabled = !isImporting && selectedFileUri != null && passphrase.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Navy950
                ),
                modifier = Modifier.testTag("import_backup_confirm_button")
            ) {
                Text(text = if (isImporting) "Decrypting & Restoring..." else "Restore Backup", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("import_backup_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    )
}
