package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.security.CryptoManager
import com.example.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val database: VaultDatabase,
    private val sessionManager: SessionManager
) {
    private val documentDao = database.documentDao()
    private val passwordDao = database.passwordDao()

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val allPasswords: Flow<List<PasswordEntity>> = passwordDao.getAllPasswords()
    val documentCount: Flow<Int> = documentDao.getDocumentCountFlow()
    val passwordCount: Flow<Int> = passwordDao.getPasswordCountFlow()

    private val vaultDocsDir: File
        get() {
            val dir = File(context.filesDir, "vault_documents")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val decryptedCacheDir: File
        get() {
            val dir = File(context.cacheDir, "decrypted")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>> {
        return if (category == "All") documentDao.getAllDocuments() else documentDao.getDocumentsByCategory(category)
    }

    fun getPasswordsByCategory(category: String): Flow<List<PasswordEntity>> {
        return if (category == "All") passwordDao.getAllPasswords() else passwordDao.getPasswordsByCategory(category)
    }

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> = documentDao.searchDocuments(query)
    fun searchPasswords(query: String): Flow<List<PasswordEntity>> = passwordDao.searchPasswords(query)

    suspend fun addDocument(
        name: String,
        category: String,
        notes: String,
        fileUri: Uri
    ): Long = withContext(Dispatchers.IO) {
        val vmk = sessionManager.getVaultMasterKey()

        var resolvedFileName = "document_${System.currentTimeMillis()}"
        var resolvedMimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
        var resolvedSize = 0L

        context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) resolvedFileName = displayName
                }
                if (sizeIndex != -1) {
                    resolvedSize = cursor.getLong(sizeIndex)
                }
            }
        }

        // Read and encrypt
        val rawBytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read source document file")

        if (resolvedSize <= 0L) {
            resolvedSize = rawBytes.size.toLong()
        }

        val docId = UUID.randomUUID().toString()
        val encFileName = "$docId.enc"
        val encFile = File(vaultDocsDir, encFileName)

        val encryptedBytes = CryptoManager.encryptBytes(rawBytes, vmk)
        encFile.writeBytes(encryptedBytes)

        val encNotes = if (notes.isNotBlank()) CryptoManager.encryptString(notes, vmk) else ""

        val entity = DocumentEntity(
            name = name.trim(),
            category = category,
            fileName = resolvedFileName,
            mimeType = resolvedMimeType,
            fileSizeBytes = resolvedSize,
            encryptedFilePath = encFileName,
            encryptedNotes = encNotes,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        documentDao.insertDocument(entity)
    }

    suspend fun updateDocument(
        id: Long,
        name: String,
        category: String,
        notes: String,
        newFileUri: Uri?
    ) = withContext(Dispatchers.IO) {
        val vmk = sessionManager.getVaultMasterKey()
        val existing = documentDao.getDocumentById(id) ?: return@withContext

        var resolvedFileName = existing.fileName
        var resolvedMimeType = existing.mimeType
        var resolvedSize = existing.fileSizeBytes
        var encFilePath = existing.encryptedFilePath

        if (newFileUri != null) {
            // Delete old encrypted file
            val oldFile = File(vaultDocsDir, existing.encryptedFilePath)
            if (oldFile.exists()) oldFile.delete()

            // Read new file
            var customName: String? = null
            context.contentResolver.query(newFileUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) customName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) resolvedSize = cursor.getLong(sizeIndex)
                }
            }
            if (!customName.isNullOrBlank()) resolvedFileName = customName!!
            resolvedMimeType = context.contentResolver.getType(newFileUri) ?: "application/octet-stream"

            val rawBytes = context.contentResolver.openInputStream(newFileUri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read new file")

            if (resolvedSize <= 0L) resolvedSize = rawBytes.size.toLong()

            val docId = UUID.randomUUID().toString()
            encFilePath = "$docId.enc"
            val encFile = File(vaultDocsDir, encFilePath)
            val encryptedBytes = CryptoManager.encryptBytes(rawBytes, vmk)
            encFile.writeBytes(encryptedBytes)
        }

        val encNotes = if (notes.isNotBlank()) CryptoManager.encryptString(notes, vmk) else ""

        val updated = existing.copy(
            name = name.trim(),
            category = category,
            fileName = resolvedFileName,
            mimeType = resolvedMimeType,
            fileSizeBytes = resolvedSize,
            encryptedFilePath = encFilePath,
            encryptedNotes = encNotes,
            modifiedAt = System.currentTimeMillis()
        )

        documentDao.updateDocument(updated)
    }

    suspend fun deleteDocument(document: DocumentEntity) = withContext(Dispatchers.IO) {
        val file = File(vaultDocsDir, document.encryptedFilePath)
        if (file.exists()) {
            file.delete()
        }
        documentDao.deleteDocument(document)
    }

    suspend fun decryptDocumentToCache(document: DocumentEntity): File = withContext(Dispatchers.IO) {
        val vmk = sessionManager.getVaultMasterKey()
        val encFile = File(vaultDocsDir, document.encryptedFilePath)
        if (!encFile.exists()) {
            throw IllegalStateException("Encrypted document file is missing on device storage")
        }

        val encryptedBytes = encFile.readBytes()
        val decryptedBytes = CryptoManager.decryptBytes(encryptedBytes, vmk)

        val cleanName = document.fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val cacheFile = File(decryptedCacheDir, "${document.id}_$cleanName")
        cacheFile.writeBytes(decryptedBytes)
        cacheFile.deleteOnExit()
        cacheFile
    }

    fun cleanupDecryptedCache() {
        try {
            val dir = File(context.cacheDir, "decrypted")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    suspend fun addPassword(
        title: String,
        category: String,
        username: String,
        accountIdentifier: String,
        plainPassword: String,
        notes: String
    ): Long = withContext(Dispatchers.IO) {
        val vmk = sessionManager.getVaultMasterKey()

        val encPassword = CryptoManager.encryptString(plainPassword, vmk)
        val encNotes = if (notes.isNotBlank()) CryptoManager.encryptString(notes, vmk) else ""

        val entity = PasswordEntity(
            title = title.trim(),
            category = category,
            username = username.trim(),
            accountIdentifier = accountIdentifier.trim(),
            encryptedPassword = encPassword,
            encryptedNotes = encNotes,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        passwordDao.insertPassword(entity)
    }

    suspend fun updatePassword(
        id: Long,
        title: String,
        category: String,
        username: String,
        accountIdentifier: String,
        plainPassword: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val vmk = sessionManager.getVaultMasterKey()
        val existing = passwordDao.getPasswordById(id) ?: return@withContext

        val encPassword = CryptoManager.encryptString(plainPassword, vmk)
        val encNotes = if (notes.isNotBlank()) CryptoManager.encryptString(notes, vmk) else ""

        val updated = existing.copy(
            title = title.trim(),
            category = category,
            username = username.trim(),
            accountIdentifier = accountIdentifier.trim(),
            encryptedPassword = encPassword,
            encryptedNotes = encNotes,
            modifiedAt = System.currentTimeMillis()
        )

        passwordDao.updatePassword(updated)
    }

    suspend fun deletePassword(password: PasswordEntity) = withContext(Dispatchers.IO) {
        passwordDao.deletePassword(password)
    }

    fun decryptPassword(entity: PasswordEntity): String {
        val vmk = sessionManager.getVaultMasterKey()
        return CryptoManager.decryptString(entity.encryptedPassword, vmk)
    }

    fun decryptNotes(encryptedNotes: String): String {
        if (encryptedNotes.isBlank()) return ""
        val vmk = sessionManager.getVaultMasterKey()
        return CryptoManager.decryptString(encryptedNotes, vmk)
    }

    suspend fun getTotalStorageBytes(): Long = withContext(Dispatchers.IO) {
        documentDao.getTotalStorageBytes() ?: 0L
    }

    suspend fun wipeAllVaultData() = withContext(Dispatchers.IO) {
        documentDao.deleteAllDocuments()
        passwordDao.deleteAllPasswords()
        vaultDocsDir.listFiles()?.forEach { it.delete() }
        cleanupDecryptedCache()
        sessionManager.wipeSecurityData()
    }
}
