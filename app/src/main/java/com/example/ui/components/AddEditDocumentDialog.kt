package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DocumentEntity
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.Navy950

@Composable
fun AddEditDocumentDialog(
    documentToEdit: DocumentEntity? = null,
    initialDecryptedNotes: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, notes: String, fileUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(documentToEdit?.name ?: "") }
    var category by remember { mutableStateOf(documentToEdit?.category ?: "Personal") }
    var notes by remember { mutableStateOf(initialDecryptedNotes) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf(documentToEdit?.fileName ?: "") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Education", "Government", "Personal", "Other")

    val allowedMimeTypes = remember { arrayOf("application/pdf", "image/jpeg", "image/jpg") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var resolvedName = "document"
            var resolvedSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        resolvedName = cursor.getString(nameIndex) ?: "document"
                    }
                    if (sizeIndex != -1) {
                        resolvedSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (resolvedSize <= 0L) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        resolvedSize = it.length
                    }
                } catch (_: Exception) {}
            }

            val ext = resolvedName.substringAfterLast('.', "").lowercase()
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val isPdf = ext == "pdf" || mime == "application/pdf"
            val isJpg = ext == "jpg" || ext == "jpeg" || mime == "image/jpeg" || mime == "image/jpg"

            if (!isPdf && !isJpg) {
                errorMessage = "Only PDF and JPG/JPEG files are allowed."
                selectedFileUri = null
                selectedFileName = ""
                return@rememberLauncherForActivityResult
            }

            if (isJpg) {
                val maxJpgSizeBytes = 4L * 1024L * 1024L // 4MB
                if (resolvedSize > maxJpgSizeBytes) {
                    val sizeMb = String.format("%.2f", resolvedSize / (1024.0 * 1024.0))
                    errorMessage = "JPG file size exceeds 4MB limit (Selected: ${sizeMb} MB)."
                    selectedFileUri = null
                    selectedFileName = ""
                    return@rememberLauncherForActivityResult
                }
            }

            selectedFileUri = uri
            selectedFileName = resolvedName
            if (name.isBlank()) {
                name = resolvedName.substringBeforeLast(".")
            }
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (documentToEdit == null) "Add Encrypted Document" else "Edit Document Information",
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
                // Document Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Document Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_form_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary
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
                            Icon(Icons.Filled.Folder, contentDescription = "Select Category", tint = CyanPrimary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doc_form_category_dropdown"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Clickable overlay over text field to ensure click is captured reliably
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { categoryMenuExpanded = true }
                            .testTag("doc_form_category_overlay")
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
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = CyanPrimary)
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

                // File Selector
                Column {
                    Text(
                        text = if (documentToEdit == null) "Select File (PDF, JPG - Max 4MB for JPG) *" else "Replace File (PDF, JPG - Max 4MB for JPG)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(allowedMimeTypes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doc_form_pick_file_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedFileName.isNotBlank()) selectedFileName else "Choose PDF or JPG File",
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Optional Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Encrypted Notes (Optional)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_form_notes_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary
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
                    if (name.isBlank()) {
                        errorMessage = "Document name is required"
                        return@Button
                    }
                    if (documentToEdit == null && selectedFileUri == null) {
                        errorMessage = "Please choose a document file to encrypt"
                        return@Button
                    }
                    onSave(name, category, notes, selectedFileUri)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = Navy950
                ),
                modifier = Modifier.testTag("doc_form_save_button")
            ) {
                Text(text = "Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("doc_form_cancel_button")
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    )
}
