package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DocPassApp
import com.example.data.DocumentEntity
import com.example.data.PasswordEntity
import com.example.security.BackupEngine
import com.example.security.PasswordGenerator
import com.example.security.PasswordPolicy
import com.example.security.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class VaultTab {
    HOME,
    DOCUMENTS,
    PASSWORDS,
    SETTINGS
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
    data class CopyToClipboard(val label: String, val text: String, val sensitiveWarning: Boolean = false) : UiEvent()
    data class OpenFile(val file: File, val mimeType: String) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DocPassApp
    private val sessionManager = app.sessionManager
    private val repository = app.repository
    private val backupEngine = app.backupEngine

    // Security & Session States
    val isLocked: StateFlow<Boolean> = sessionManager.isLocked
    val isMasterPinSet: StateFlow<Boolean> = sessionManager.isMasterPinSet
    val biometricEnabled: StateFlow<Boolean> = sessionManager.biometricEnabled
    val autoLockTimeoutMs: StateFlow<Long> = sessionManager.autoLockTimeoutMs
    val themeMode: StateFlow<ThemeMode> = sessionManager.themeMode

    // Navigation & UI States
    private val _activeTab = MutableStateFlow(VaultTab.HOME)
    val activeTab: StateFlow<VaultTab> = _activeTab.asStateFlow()

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _isGlobalSearchOpen = MutableStateFlow(false)
    val isGlobalSearchOpen: StateFlow<Boolean> = _isGlobalSearchOpen.asStateFlow()

    // Filter Chips
    private val _documentCategoryFilter = MutableStateFlow("All")
    val documentCategoryFilter: StateFlow<String> = _documentCategoryFilter.asStateFlow()

    private val _documentSearchQuery = MutableStateFlow("")
    val documentSearchQuery: StateFlow<String> = _documentSearchQuery.asStateFlow()

    private val _passwordCategoryFilter = MutableStateFlow("All")
    val passwordCategoryFilter: StateFlow<String> = _passwordCategoryFilter.asStateFlow()

    private val _passwordSearchQuery = MutableStateFlow("")
    val passwordSearchQuery: StateFlow<String> = _passwordSearchQuery.asStateFlow()

    // Storage info
    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    // Events
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Data streams from Room
    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPasswords: StateFlow<List<PasswordEntity>> = repository.allPasswords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documentCount: StateFlow<Int> = repository.documentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val passwordCount: StateFlow<Int> = repository.passwordCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Filtered Document List
    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        _documentCategoryFilter,
        _documentSearchQuery
    ) { docs, category, query ->
        docs.filter { doc ->
            val matchCategory = category == "All" || doc.category.equals(category, ignoreCase = true)
            val matchQuery = query.isBlank() || doc.name.contains(query, ignoreCase = true) || doc.fileName.contains(query, ignoreCase = true)
            matchCategory && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Password List
    val filteredPasswords: StateFlow<List<PasswordEntity>> = combine(
        allPasswords,
        _passwordCategoryFilter,
        _passwordSearchQuery
    ) { passwords, category, query ->
        passwords.filter { item ->
            val matchCategory = category == "All" || item.category.equals(category, ignoreCase = true)
            val matchQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.accountIdentifier.contains(query, ignoreCase = true)
            matchCategory && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Global Search Results
    val globalSearchResults: StateFlow<Pair<List<DocumentEntity>, List<PasswordEntity>>> = combine(
        allDocuments,
        allPasswords,
        _globalSearchQuery
    ) { docs, passwords, query ->
        if (query.isBlank()) {
            Pair(emptyList(), emptyList())
        } else {
            val matchedDocs = docs.filter {
                it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) || it.fileName.contains(query, ignoreCase = true)
            }
            val matchedPass = passwords.filter {
                it.title.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) || it.username.contains(query, ignoreCase = true) || it.accountIdentifier.contains(query, ignoreCase = true)
            }
            Pair(matchedDocs, matchedPass)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(emptyList(), emptyList()))

    // Active Dialog States
    var selectedDocument = MutableStateFlow<DocumentEntity?>(null)
    var selectedPassword = MutableStateFlow<PasswordEntity?>(null)

    var showAddDocumentDialog = MutableStateFlow(false)
    var showEditDocumentDialog = MutableStateFlow<DocumentEntity?>(null)
    var showAddPasswordDialog = MutableStateFlow(false)
    var showEditPasswordDialog = MutableStateFlow<PasswordEntity?>(null)
    var showChangePinDialog = MutableStateFlow(false)
    var showPasswordGeneratorDialog = MutableStateFlow(false)
    var showExportBackupDialog = MutableStateFlow(false)
    var showImportBackupDialog = MutableStateFlow(false)
    var showWipeVaultDialog = MutableStateFlow(false)

    init {
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = repository.getTotalStorageBytes()
            _totalStorageBytes.value = bytes
        }
    }

    fun setActiveTab(tab: VaultTab) {
        _activeTab.value = tab
        recordUserActivity()
    }

    fun setGlobalSearchOpen(open: Boolean) {
        _isGlobalSearchOpen.value = open
        if (!open) _globalSearchQuery.value = ""
        recordUserActivity()
    }

    fun setGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
        recordUserActivity()
    }

    fun setDocumentCategoryFilter(category: String) {
        _documentCategoryFilter.value = category
        recordUserActivity()
    }

    fun setDocumentSearchQuery(query: String) {
        _documentSearchQuery.value = query
        recordUserActivity()
    }

    fun setPasswordCategoryFilter(category: String) {
        _passwordCategoryFilter.value = category
        recordUserActivity()
    }

    fun setPasswordSearchQuery(query: String) {
        _passwordSearchQuery.value = query
        recordUserActivity()
    }

    fun recordUserActivity() {
        sessionManager.updateUserActivity()
    }

    fun checkAutoLock() {
        sessionManager.checkAutoLockOnInactivity()
    }

    // Authentication Actions
    fun setupMasterPin(pin: String, onResult: (Boolean, String?) -> Unit) {
        val validation = PasswordPolicy.validate(pin)
        if (!validation.isValid) {
            val err = validation.errors.firstOrNull() ?: "Password does not meet security requirements"
            onResult(false, err)
            return
        }
        val success = sessionManager.setupMasterPin(pin)
        if (success) {
            onResult(true, null)
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowSnackbar("Master Password configured. Vault is now secured."))
            }
        } else {
            onResult(false, "Failed to initialize cryptographic master key")
        }
    }

    fun unlockWithPin(pin: String, onResult: (Boolean, String?) -> Unit) {
        val success = sessionManager.unlockWithPin(pin)
        if (success) {
            onResult(true, null)
            refreshStorageStats()
        } else {
            onResult(false, "Incorrect Master Password. Access denied.")
        }
    }

    fun unlockWithBiometrics(onResult: (Boolean) -> Unit) {
        val success = sessionManager.unlockWithSavedSession()
        onResult(success)
        if (success) {
            refreshStorageStats()
        }
    }

    fun lockNow() {
        sessionManager.lockVault()
        repository.cleanupDecryptedCache()
        selectedDocument.value = null
        selectedPassword.value = null
        _isGlobalSearchOpen.value = false
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowSnackbar("Vault locked."))
        }
    }

    fun changeMasterPin(oldPin: String, newPin: String, onResult: (Boolean, String?) -> Unit) {
        val validation = PasswordPolicy.validate(newPin)
        if (!validation.isValid) {
            val err = validation.errors.firstOrNull() ?: "New password does not meet security requirements"
            onResult(false, err)
            return
        }
        val success = sessionManager.changeMasterPin(oldPin, newPin)
        if (success) {
            onResult(true, null)
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowSnackbar("Master Password changed successfully."))
            }
        } else {
            onResult(false, "Current password is incorrect.")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        sessionManager.setBiometricEnabled(enabled)
        recordUserActivity()
    }

    fun setAutoLockTimeout(timeoutMs: Long) {
        sessionManager.setAutoLockTimeout(timeoutMs)
        recordUserActivity()
    }

    fun setThemeMode(mode: ThemeMode) {
        sessionManager.setThemeMode(mode)
        recordUserActivity()
    }

    // Document Vault Actions
    fun addDocument(
        name: String,
        category: String,
        notes: String,
        uri: Uri,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addDocument(name, category, notes, uri)
                refreshStorageStats()
                onComplete(true, null)
                _uiEvents.emit(UiEvent.ShowSnackbar("Document encrypted & stored securely."))
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to save encrypted document")
            }
        }
    }

    fun addMultipleDocuments(
        uris: List<Uri>,
        category: String = "Personal",
        onComplete: (Int, List<String>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (successCount, errors) = repository.addMultipleDocuments(uris, category)
                refreshStorageStats()
                onComplete(successCount, errors)
                if (successCount > 0) {
                    val msg = if (errors.isEmpty()) {
                        "$successCount documents encrypted and stored in vault."
                    } else {
                        "$successCount documents stored (${errors.size} skipped/failed)."
                    }
                    _uiEvents.emit(UiEvent.ShowSnackbar(msg))
                } else if (errors.isNotEmpty()) {
                    _uiEvents.emit(UiEvent.ShowSnackbar(errors.first()))
                }
            } catch (e: Exception) {
                onComplete(0, listOf(e.message ?: "Failed to import documents"))
            }
        }
    }

    fun updateDocument(
        id: Long,
        name: String,
        category: String,
        notes: String,
        newUri: Uri?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updateDocument(id, name, category, notes, newUri)
                refreshStorageStats()
                onComplete(true, null)
                _uiEvents.emit(UiEvent.ShowSnackbar("Document updated."))
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to update document")
            }
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            try {
                repository.deleteDocument(document)
                refreshStorageStats()
                if (selectedDocument.value?.id == document.id) {
                    selectedDocument.value = null
                }
                _uiEvents.emit(UiEvent.ShowSnackbar("Document deleted permanently."))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Error deleting document: ${e.message}"))
            }
        }
    }

    fun decryptAndOpenDocument(document: DocumentEntity) {
        viewModelScope.launch {
            try {
                val decryptedFile = repository.decryptDocumentToCache(document)
                _uiEvents.emit(UiEvent.OpenFile(decryptedFile, document.mimeType))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Decryption failed: ${e.message}"))
            }
        }
    }

    // Password Vault Actions
    fun addPassword(
        title: String,
        category: String,
        username: String,
        accountIdentifier: String,
        plainPassword: String,
        notes: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (title.isBlank() || plainPassword.isBlank()) {
            onComplete(false, "App name and password are required")
            return
        }
        viewModelScope.launch {
            try {
                repository.addPassword(title, category, username, accountIdentifier, plainPassword, notes)
                onComplete(true, null)
                _uiEvents.emit(UiEvent.ShowSnackbar("Password encrypted & stored in vault."))
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to save password")
            }
        }
    }

    fun updatePassword(
        id: Long,
        title: String,
        category: String,
        username: String,
        accountIdentifier: String,
        plainPassword: String,
        notes: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.updatePassword(id, title, category, username, accountIdentifier, plainPassword, notes)
                onComplete(true, null)
                _uiEvents.emit(UiEvent.ShowSnackbar("Password entry updated."))
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to update password")
            }
        }
    }

    fun deletePassword(password: PasswordEntity) {
        viewModelScope.launch {
            try {
                repository.deletePassword(password)
                if (selectedPassword.value?.id == password.id) {
                    selectedPassword.value = null
                }
                _uiEvents.emit(UiEvent.ShowSnackbar("Password entry removed."))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Error deleting password entry: ${e.message}"))
            }
        }
    }

    fun decryptPassword(entity: PasswordEntity): String {
        return try {
            repository.decryptPassword(entity)
        } catch (e: Exception) {
            "Error decrypting"
        }
    }

    fun decryptNotes(encryptedNotes: String): String {
        return try {
            repository.decryptNotes(encryptedNotes)
        } catch (e: Exception) {
            ""
        }
    }

    fun copyToClipboard(label: String, text: String, isSensitive: Boolean = false) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.CopyToClipboard(label, text, isSensitive))
        }
    }

    // Encrypted Backup & Restore
    fun exportBackup(uri: Uri, passphrase: String, onResult: (BackupEngine.BackupResult) -> Unit) {
        viewModelScope.launch {
            val res = backupEngine.exportEncryptedBackup(uri, passphrase)
            onResult(res)
            if (res.success) {
                _uiEvents.emit(UiEvent.ShowSnackbar("Encrypted backup exported successfully (${res.documentCount} docs, ${res.passwordCount} passwords)."))
            }
        }
    }

    fun importBackup(uri: Uri, passphrase: String, onResult: (BackupEngine.BackupResult) -> Unit) {
        viewModelScope.launch {
            val res = backupEngine.importEncryptedBackup(uri, passphrase)
            onResult(res)
            if (res.success) {
                refreshStorageStats()
                _uiEvents.emit(UiEvent.ShowSnackbar("Vault backup imported (${res.documentCount} docs, ${res.passwordCount} passwords restored)."))
            }
        }
    }

    // Factory Reset
    fun wipeVault(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.wipeAllVaultData()
            refreshStorageStats()
            onComplete()
            _uiEvents.emit(UiEvent.ShowSnackbar("All vault data wiped."))
        }
    }
}
