package com.example.security

import android.content.Context
import android.net.Uri
import com.example.data.DocumentEntity
import com.example.data.PasswordEntity
import com.example.data.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Arrays
import java.util.UUID

/**
 * Production-grade Encrypted Backup & Restore Engine for Privault.
 *
 * All exported backups are strongly encrypted using AES-256-GCM with a user-supplied
 * password or Master PIN before being written to disk/file.
 * Backups NEVER contain plaintext passwords or unencrypted documents.
 */
class BackupEngine(
    private val context: Context,
    private val database: VaultDatabase,
    private val sessionManager: SessionManager
) {

    data class BackupResult(
        val success: Boolean,
        val documentCount: Int = 0,
        val passwordCount: Int = 0,
        val errorMessage: String? = null
    )

    /**
     * Exports entire vault to an encrypted backup file at the provided destination Uri.
     */
    suspend fun exportEncryptedBackup(destinationUri: Uri, backupPassphrase: String): BackupResult =
        withContext(Dispatchers.IO) {
            val passChars = backupPassphrase.toCharArray()
            try {
                val vmk = sessionManager.getVaultMasterKey()
                val docDao = database.documentDao()
                val passDao = database.passwordDao()

                // Read all raw entities
                val docList = mutableListOf<DocumentEntity>()
                // Collect current list
                docList.addAll(docDao.getDocumentsByCategory("Education").let { emptyList() }) // placeholder, query direct
                // Fetch direct from DB cursor or query
                val allDocs = mutableListOf<DocumentEntity>()
                val allPass = mutableListOf<PasswordEntity>()

                // Use direct queries
                val docsCursor = database.query("SELECT * FROM documents", null)
                docsCursor.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow("id")
                    val nameCol = cursor.getColumnIndexOrThrow("name")
                    val catCol = cursor.getColumnIndexOrThrow("category")
                    val fileCol = cursor.getColumnIndexOrThrow("fileName")
                    val mimeCol = cursor.getColumnIndexOrThrow("mimeType")
                    val sizeCol = cursor.getColumnIndexOrThrow("fileSizeBytes")
                    val pathCol = cursor.getColumnIndexOrThrow("encryptedFilePath")
                    val notesCol = cursor.getColumnIndexOrThrow("encryptedNotes")
                    val createdCol = cursor.getColumnIndexOrThrow("createdAt")
                    val modCol = cursor.getColumnIndexOrThrow("modifiedAt")

                    while (cursor.moveToNext()) {
                        allDocs.add(
                            DocumentEntity(
                                id = cursor.getLong(idCol),
                                name = cursor.getString(nameCol),
                                category = cursor.getString(catCol),
                                fileName = cursor.getString(fileCol),
                                mimeType = cursor.getString(mimeCol),
                                fileSizeBytes = cursor.getLong(sizeCol),
                                encryptedFilePath = cursor.getString(pathCol),
                                encryptedNotes = cursor.getString(notesCol),
                                createdAt = cursor.getLong(createdCol),
                                modifiedAt = cursor.getLong(modCol)
                            )
                        )
                    }
                }

                val passCursor = database.query("SELECT * FROM passwords", null)
                passCursor.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow("id")
                    val titleCol = cursor.getColumnIndexOrThrow("title")
                    val catCol = cursor.getColumnIndexOrThrow("category")
                    val userCol = cursor.getColumnIndexOrThrow("username")
                    val accCol = cursor.getColumnIndexOrThrow("accountIdentifier")
                    val pwdCol = cursor.getColumnIndexOrThrow("encryptedPassword")
                    val notesCol = cursor.getColumnIndexOrThrow("encryptedNotes")
                    val createdCol = cursor.getColumnIndexOrThrow("createdAt")
                    val modCol = cursor.getColumnIndexOrThrow("modifiedAt")

                    while (cursor.moveToNext()) {
                        allPass.add(
                            PasswordEntity(
                                id = cursor.getLong(idCol),
                                title = cursor.getString(titleCol),
                                category = cursor.getString(catCol),
                                username = cursor.getString(userCol),
                                accountIdentifier = cursor.getString(accCol),
                                encryptedPassword = cursor.getString(pwdCol),
                                encryptedNotes = cursor.getString(notesCol),
                                createdAt = cursor.getLong(createdCol),
                                modifiedAt = cursor.getLong(modCol)
                            )
                        )
                    }
                }

                // Build backup JSON structure
                val rootJson = JSONObject()
                rootJson.put("version", 1)
                rootJson.put("app", "DocPass")
                rootJson.put("timestamp", System.currentTimeMillis())

                val passArray = JSONArray()
                for (p in allPass) {
                    val pObj = JSONObject()
                    pObj.put("title", p.title)
                    pObj.put("category", p.category)
                    pObj.put("username", p.username)
                    pObj.put("accountIdentifier", p.accountIdentifier)
                    // Decrypt with current VMK and keep in bundle
                    val rawPassword = CryptoManager.decryptString(p.encryptedPassword, vmk)
                    val rawNotes = if (p.encryptedNotes.isNotEmpty()) CryptoManager.decryptString(p.encryptedNotes, vmk) else ""
                    pObj.put("password", rawPassword)
                    pObj.put("notes", rawNotes)
                    pObj.put("createdAt", p.createdAt)
                    pObj.put("modifiedAt", p.modifiedAt)
                    passArray.put(pObj)
                }
                rootJson.put("passwords", passArray)

                val docsDir = File(context.filesDir, "vault_documents")
                val docsArray = JSONArray()
                for (d in allDocs) {
                    val docFile = File(docsDir, d.encryptedFilePath)
                    if (docFile.exists()) {
                        val dObj = JSONObject()
                        dObj.put("name", d.name)
                        dObj.put("category", d.category)
                        dObj.put("fileName", d.fileName)
                        dObj.put("mimeType", d.mimeType)
                        dObj.put("fileSizeBytes", d.fileSizeBytes)
                        val rawNotes = if (d.encryptedNotes.isNotEmpty()) CryptoManager.decryptString(d.encryptedNotes, vmk) else ""
                        dObj.put("notes", rawNotes)
                        dObj.put("createdAt", d.createdAt)
                        dObj.put("modifiedAt", d.modifiedAt)

                        // Decrypt document bytes for the bundle
                        val encBytes = docFile.readBytes()
                        val rawBytes = CryptoManager.decryptBytes(encBytes, vmk)
                        dObj.put("contentBase64", android.util.Base64.encodeToString(rawBytes, android.util.Base64.NO_WRAP))
                        docsArray.put(dObj)
                    }
                }
                rootJson.put("documents", docsArray)

                val rawJsonBytes = rootJson.toString().toByteArray(Charsets.UTF_8)

                // Encrypt whole bundle with Backup Passphrase + Salt
                val backupSalt = CryptoManager.generateRandomBytes(32)
                val backupKey = CryptoManager.deriveKeyFromPin(passChars, backupSalt)
                val encryptedBackupBytes = CryptoManager.encryptBytes(rawJsonBytes, backupKey)

                // Write [Header "DOCPASS_ENC_V1"] + [32-byte Salt] + [Encrypted Payload]
                context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                    outStream.write("DOCPASS_ENC_V1".toByteArray(Charsets.UTF_8))
                    outStream.write(backupSalt)
                    outStream.write(encryptedBackupBytes)
                    outStream.flush()
                } ?: return@withContext BackupResult(false, errorMessage = "Failed to open output stream")

                BackupResult(
                    success = true,
                    documentCount = docsArray.length(),
                    passwordCount = passArray.length()
                )
            } catch (e: Exception) {
                BackupResult(false, errorMessage = e.message ?: "Export failed")
            } finally {
                Arrays.fill(passChars, '\u0000')
            }
        }

    /**
     * Imports an encrypted backup file from the provided source Uri.
     */
    suspend fun importEncryptedBackup(sourceUri: Uri, backupPassphrase: String): BackupResult =
        withContext(Dispatchers.IO) {
            val passChars = backupPassphrase.toCharArray()
            try {
                val vmk = sessionManager.getVaultMasterKey()
                val docDao = database.documentDao()
                val passDao = database.passwordDao()

                val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                    ?: return@withContext BackupResult(false, errorMessage = "Cannot read backup file")

                val validHeaders = listOf("DOCPASS_ENC_V1", "PRIVAULT_ENC_V1")
                var matchedHeader: String? = null
                for (h in validHeaders) {
                    val hBytes = h.toByteArray(Charsets.UTF_8)
                    if (bytes.size >= hBytes.size + 32 + 12) {
                        val fileHeader = String(bytes, 0, hBytes.size, Charsets.UTF_8)
                        if (fileHeader == h) {
                            matchedHeader = h
                            break
                        }
                    }
                }

                if (matchedHeader == null) {
                    return@withContext BackupResult(false, errorMessage = "Unrecognized or corrupted backup file format")
                }

                val headerBytesLength = matchedHeader.toByteArray(Charsets.UTF_8).size

                // Extract salt
                val salt = ByteArray(32)
                System.arraycopy(bytes, headerBytesLength, salt, 0, 32)

                // Extract ciphertext
                val cipherOffset = headerBytesLength + 32
                val cipherLength = bytes.size - cipherOffset
                val cipherBytes = ByteArray(cipherLength)
                System.arraycopy(bytes, cipherOffset, cipherBytes, 0, cipherLength)

                // Derive key & decrypt bundle
                val backupKey = CryptoManager.deriveKeyFromPin(passChars, salt)
                val decryptedJsonBytes = try {
                    CryptoManager.decryptBytes(cipherBytes, backupKey)
                } catch (e: Exception) {
                    return@withContext BackupResult(false, errorMessage = "Incorrect backup password or corrupted data")
                }

                val jsonString = String(decryptedJsonBytes, Charsets.UTF_8)
                val rootJson = JSONObject(jsonString)

                val docsDir = File(context.filesDir, "vault_documents")
                if (!docsDir.exists()) docsDir.mkdirs()

                var importedPassCount = 0
                var importedDocCount = 0

                // Import Passwords
                if (rootJson.has("passwords")) {
                    val passArray = rootJson.getJSONArray("passwords")
                    for (i in 0 until passArray.length()) {
                        val pObj = passArray.getJSONObject(i)
                        val title = pObj.getString("title")
                        val category = pObj.getString("category")
                        val username = pObj.getString("username")
                        val accountIdentifier = pObj.optString("accountIdentifier", "")
                        val rawPassword = pObj.getString("password")
                        val rawNotes = pObj.optString("notes", "")
                        val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                        val modifiedAt = pObj.optLong("modifiedAt", System.currentTimeMillis())

                        val encPass = CryptoManager.encryptString(rawPassword, vmk)
                        val encNotes = if (rawNotes.isNotEmpty()) CryptoManager.encryptString(rawNotes, vmk) else ""

                        passDao.insertPassword(
                            PasswordEntity(
                                title = title,
                                category = category,
                                username = username,
                                accountIdentifier = accountIdentifier,
                                encryptedPassword = encPass,
                                encryptedNotes = encNotes,
                                createdAt = createdAt,
                                modifiedAt = modifiedAt
                            )
                        )
                        importedPassCount++
                    }
                }

                // Import Documents
                if (rootJson.has("documents")) {
                    val docsArray = rootJson.getJSONArray("documents")
                    for (i in 0 until docsArray.length()) {
                        val dObj = docsArray.getJSONObject(i)
                        val name = dObj.getString("name")
                        val category = dObj.getString("category")
                        val fileName = dObj.getString("fileName")
                        val mimeType = dObj.getString("mimeType")
                        val fileSizeBytes = dObj.optLong("fileSizeBytes", 0L)
                        val rawNotes = dObj.optString("notes", "")
                        val createdAt = dObj.optLong("createdAt", System.currentTimeMillis())
                        val modifiedAt = dObj.optLong("modifiedAt", System.currentTimeMillis())
                        val base64Content = dObj.getString("contentBase64")
                        val rawDocBytes = android.util.Base64.decode(base64Content, android.util.Base64.NO_WRAP)

                        val docId = UUID.randomUUID().toString()
                        val encFileName = "$docId.enc"
                        val destFile = File(docsDir, encFileName)

                        val encryptedDocBytes = CryptoManager.encryptBytes(rawDocBytes, vmk)
                        destFile.writeBytes(encryptedDocBytes)

                        val encNotes = if (rawNotes.isNotEmpty()) CryptoManager.encryptString(rawNotes, vmk) else ""

                        docDao.insertDocument(
                            DocumentEntity(
                                name = name,
                                category = category,
                                fileName = fileName,
                                mimeType = mimeType,
                                fileSizeBytes = if (fileSizeBytes > 0) fileSizeBytes else rawDocBytes.size.toLong(),
                                encryptedFilePath = encFileName,
                                encryptedNotes = encNotes,
                                createdAt = createdAt,
                                modifiedAt = modifiedAt
                            )
                        )
                        importedDocCount++
                    }
                }

                BackupResult(
                    success = true,
                    documentCount = importedDocCount,
                    passwordCount = importedPassCount
                )
            } catch (e: Exception) {
                BackupResult(false, errorMessage = e.message ?: "Import failed")
            } finally {
                Arrays.fill(passChars, '\u0000')
            }
        }
}
