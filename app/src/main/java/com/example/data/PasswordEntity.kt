package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String, // App / Website name
    val category: String, // Bank, ATM, Education, Social, Shopping, Other
    val username: String,
    val accountIdentifier: String = "", // Optional account number, card last 4, etc.
    val encryptedPassword: String, // AES-GCM encrypted
    val encryptedNotes: String = "", // AES-GCM encrypted
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
