package com.example

import android.app.Application
import com.example.data.VaultDatabase
import com.example.data.VaultRepository
import com.example.security.BackupEngine
import com.example.security.SessionManager

class DocPassApp : Application() {
    lateinit var sessionManager: SessionManager
        private set

    lateinit var database: VaultDatabase
        private set

    lateinit var repository: VaultRepository
        private set

    lateinit var backupEngine: BackupEngine
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        database = VaultDatabase.getDatabase(this)
        repository = VaultRepository(this, database, sessionManager)
        backupEngine = BackupEngine(this, database, sessionManager)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            sessionManager.checkAutoLockOnInactivity()
            repository.cleanupDecryptedCache()
        }
    }
}
