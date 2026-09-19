package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY modifiedAt DESC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getPasswordById(id: Long): PasswordEntity?

    @Query("SELECT * FROM passwords WHERE category = :category ORDER BY modifiedAt DESC")
    fun getPasswordsByCategory(category: String): Flow<List<PasswordEntity>>

    // Note: Search searches only non-sensitive fields (title, category, username, accountIdentifier).
    // Sensitive passwords are NEVER searched or exposed in plaintext queries.
    @Query("SELECT * FROM passwords WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR accountIdentifier LIKE '%' || :query || '%' ORDER BY modifiedAt DESC")
    fun searchPasswords(query: String): Flow<List<PasswordEntity>>

    @Query("SELECT COUNT(*) FROM passwords")
    fun getPasswordCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun getPasswordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntity): Long

    @Update
    suspend fun updatePassword(password: PasswordEntity)

    @Delete
    suspend fun deletePassword(password: PasswordEntity)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: Long)

    @Query("DELETE FROM passwords")
    suspend fun deleteAllPasswords()
}
