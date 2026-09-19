package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // Education, Government, Personal, Other
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val encryptedFilePath: String, // Relative path inside private filesDir/vault_docs
    val encryptedNotes: String = "", // AES-GCM encrypted
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
