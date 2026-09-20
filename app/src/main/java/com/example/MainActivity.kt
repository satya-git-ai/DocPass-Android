package com.example

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.DocumentEntity
import com.example.data.PasswordEntity
import com.example.ui.MainViewModel
import com.example.ui.UiEvent
import com.example.ui.VaultTab
import com.example.ui.components.AddEditDocumentDialog
import com.example.ui.components.AddEditPasswordDialog
import com.example.ui.components.ChangePinDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DocumentDetailDialog
import com.example.ui.components.ExportBackupDialog
import com.example.ui.components.GiveTipDialog
import com.example.ui.components.ImportBackupDialog
import com.example.ui.components.PasswordDetailDialog
import com.example.ui.components.PasswordGeneratorDialog
import com.example.ui.components.PrivaultBottomBar
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.GlobalSearchScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.PasswordsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PrivaultTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            PrivaultTheme(themeMode = themeMode) {
                PrivaultMainApp(viewModel = viewModel)
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.recordUserActivity()
    }

    override fun onStop() {
        super.onStop()
        viewModel.checkAutoLock()
    }
}

@Composable
fun PrivaultMainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isLocked by viewModel.isLocked.collectAsState()
    val isMasterPinSet by viewModel.isMasterPinSet.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val autoLockTimeoutMs by viewModel.autoLockTimeoutMs.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val activeTab by viewModel.activeTab.collectAsState()
    val isGlobalSearchOpen by viewModel.isGlobalSearchOpen.collectAsState()
    val globalSearchQuery by viewModel.globalSearchQuery.collectAsState()
    val globalSearchResults by viewModel.globalSearchResults.collectAsState()

    val documents by viewModel.filteredDocuments.collectAsState()
    val passwords by viewModel.filteredPasswords.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val allPwds by viewModel.allPasswords.collectAsState()
    val documentCount by viewModel.documentCount.collectAsState()
    val passwordCount by viewModel.passwordCount.collectAsState()
    val totalStorageBytes by viewModel.totalStorageBytes.collectAsState()

    val docCategoryFilter by viewModel.documentCategoryFilter.collectAsState()
    val docSearchQuery by viewModel.documentSearchQuery.collectAsState()
    val pwdCategoryFilter by viewModel.passwordCategoryFilter.collectAsState()
    val pwdSearchQuery by viewModel.passwordSearchQuery.collectAsState()

    // Dialog state holders
    var showAddDocDialog by remember { mutableStateOf(false) }
    var docToEdit by remember { mutableStateOf<DocumentEntity?>(null) }
    var selectedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    var showAddPwdDialog by remember { mutableStateOf(false) }
    var pwdToEdit by remember { mutableStateOf<PasswordEntity?>(null) }
    var selectedPwd by remember { mutableStateOf<PasswordEntity?>(null) }
    var pwdToDelete by remember { mutableStateOf<PasswordEntity?>(null) }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showPasswordGeneratorDialog by remember { mutableStateOf(false) }
    var showTipDialog by remember { mutableStateOf(false) }
    var showExportBackupDialog by remember { mutableStateOf(false) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var showWipeVaultDialog by remember { mutableStateOf(false) }

    // Lifecycle auto-lock observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.checkAutoLock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle UI Events
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is UiEvent.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(event.label, event.text)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && event.sensitiveWarning) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            clip.description.extras = android.os.PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "${event.label} copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
                is UiEvent.OpenFile -> {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            event.file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, event.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open Decrypted Document"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open file format: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("privault_root_layout"),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isLocked && !isGlobalSearchOpen) {
                PrivaultBottomBar(
                    currentTab = activeTab,
                    onTabSelected = { viewModel.setActiveTab(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = isLocked,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "lock_transition"
            ) { locked ->
                if (locked) {
                    LockScreen(
                        isMasterPinSet = isMasterPinSet,
                        biometricEnabled = biometricEnabled,
                        onSetupMasterPin = { pin, callback ->
                            viewModel.setupMasterPin(pin, callback)
                        },
                        onUnlockWithPin = { pin, callback ->
                            viewModel.unlockWithPin(pin, callback)
                        },
                        onUnlockWithBiometrics = { callback ->
                            viewModel.unlockWithBiometrics(callback)
                        }
                    )
                } else if (isGlobalSearchOpen) {
                    GlobalSearchScreen(
                        searchQuery = globalSearchQuery,
                        onQueryChange = { viewModel.setGlobalSearchQuery(it) },
                        searchResults = globalSearchResults,
                        onCloseSearch = { viewModel.setGlobalSearchOpen(false) },
                        onSelectDocument = { doc ->
                            selectedDoc = doc
                        },
                        onSelectPassword = { pwd ->
                            selectedPwd = pwd
                        }
                    )
                } else {
                    when (activeTab) {
                        VaultTab.HOME -> {
                            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                            DashboardScreen(
                                documentCount = documentCount,
                                passwordCount = passwordCount,
                                totalStorageBytes = totalStorageBytes,
                                themeMode = themeMode,
                                isBiometricEnabled = biometricEnabled,
                                onToggleTheme = {
                                    val isCurrentlyDark = when (themeMode) {
                                        com.example.security.ThemeMode.DARK -> true
                                        com.example.security.ThemeMode.LIGHT -> false
                                        com.example.security.ThemeMode.SYSTEM -> isSystemDark
                                    }
                                    viewModel.setThemeMode(
                                        if (isCurrentlyDark) com.example.security.ThemeMode.LIGHT
                                        else com.example.security.ThemeMode.DARK
                                    )
                                },
                                onNavigateTab = { viewModel.setActiveTab(it) },
                                onNavigateToSettings = { viewModel.setActiveTab(VaultTab.SETTINGS) },
                                onOpenGlobalSearch = { viewModel.setGlobalSearchOpen(true) },
                                onLockNow = { viewModel.lockNow() },
                                onAddDocument = { showAddDocDialog = true },
                                onAddPassword = { showAddPwdDialog = true },
                                onOpenPasswordGenerator = { showPasswordGeneratorDialog = true },
                                onGiveTip = { showTipDialog = true }
                            )
                        }
                        VaultTab.DOCUMENTS -> {
                            DocumentsScreen(
                                documents = documents,
                                selectedCategory = docCategoryFilter,
                                searchQuery = docSearchQuery,
                                onCategorySelected = { viewModel.setDocumentCategoryFilter(it) },
                                onSearchQueryChange = { viewModel.setDocumentSearchQuery(it) },
                                onAddDocument = { showAddDocDialog = true },
                                onUploadMultipleFiles = { uris ->
                                    val cat = if (docCategoryFilter != "All") docCategoryFilter else "Personal"
                                    viewModel.addMultipleDocuments(uris, cat) { _, _ -> }
                                },
                                onSelectDocument = { selectedDoc = it }
                            )
                        }
                        VaultTab.PASSWORDS -> {
                            PasswordsScreen(
                                passwords = passwords,
                                selectedCategory = pwdCategoryFilter,
                                searchQuery = pwdSearchQuery,
                                onCategorySelected = { viewModel.setPasswordCategoryFilter(it) },
                                onSearchQueryChange = { viewModel.setPasswordSearchQuery(it) },
                                onAddPassword = { showAddPwdDialog = true },
                                onSelectPassword = { selectedPwd = it },
                                onDecryptPassword = { viewModel.decryptPassword(it) },
                                onCopyPassword = { label, plain, isHighRisk ->
                                    viewModel.copyToClipboard(label, plain, isHighRisk)
                                }
                            )
                        }
                        VaultTab.SETTINGS -> {
                            SettingsScreen(
                                biometricEnabled = biometricEnabled,
                                autoLockTimeoutMs = autoLockTimeoutMs,
                                themeMode = themeMode,
                                documentCount = documentCount,
                                passwordCount = passwordCount,
                                totalStorageBytes = totalStorageBytes,
                                onChangePin = { showChangePinDialog = true },
                                onToggleBiometric = { viewModel.setBiometricEnabled(it) },
                                onSetAutoLockTimeout = { viewModel.setAutoLockTimeout(it) },
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onExportBackup = { showExportBackupDialog = true },
                                onImportBackup = { showImportBackupDialog = true },
                                onWipeVault = { showWipeVaultDialog = true },
                                onLockNow = { viewModel.lockNow() },
                                onGiveTip = { showTipDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Document Dialog
    if (showAddDocDialog) {
        val defaultDocCategory = if (docCategoryFilter != "All") docCategoryFilter else "Personal"
        AddEditDocumentDialog(
            documentToEdit = null,
            initialCategory = defaultDocCategory,
            initialDecryptedNotes = "",
            onDismiss = { showAddDocDialog = false },
            onSave = { name, category, notes, uri ->
                if (uri != null) {
                    viewModel.addDocument(name, category, notes, uri) { success, _ ->
                        if (success) showAddDocDialog = false
                    }
                }
            }
        )
    }

    // Edit Document Dialog
    if (docToEdit != null) {
        val currentDoc = docToEdit!!
        val decryptedNotes = remember(currentDoc) { viewModel.decryptNotes(currentDoc.encryptedNotes) }
        AddEditDocumentDialog(
            documentToEdit = currentDoc,
            initialCategory = currentDoc.category,
            initialDecryptedNotes = decryptedNotes,
            onDismiss = { docToEdit = null },
            onSave = { name, category, notes, newUri ->
                viewModel.updateDocument(currentDoc.id, name, category, notes, newUri) { success, _ ->
                    if (success) {
                        docToEdit = null
                        selectedDoc = null
                    }
                }
            }
        )
    }

    // Document Detail Dialog
    if (selectedDoc != null) {
        val currentDoc = selectedDoc!!
        val decryptedNotes = remember(currentDoc) { viewModel.decryptNotes(currentDoc.encryptedNotes) }
        DocumentDetailDialog(
            document = currentDoc,
            decryptedNotes = decryptedNotes,
            onDismiss = { selectedDoc = null },
            onOpenDocument = { viewModel.decryptAndOpenDocument(currentDoc) },
            onEdit = {
                docToEdit = currentDoc
            },
            onDelete = {
                docToDelete = currentDoc
            }
        )
    }

    // Delete Document Confirm Dialog
    if (docToDelete != null) {
        val doc = docToDelete!!
        ConfirmDeleteDialog(
            title = "Delete Document",
            message = "Are you sure you want to permanently delete “${doc.name}”? The encrypted file will be permanently removed from device storage.",
            confirmButtonText = "Delete",
            onDismiss = { docToDelete = null },
            onConfirm = {
                viewModel.deleteDocument(doc)
                selectedDoc = null
                docToDelete = null
            }
        )
    }

    // Add Password Dialog
    if (showAddPwdDialog) {
        val defaultPwdCategory = if (pwdCategoryFilter != "All") pwdCategoryFilter else "Bank"
        AddEditPasswordDialog(
            passwordToEdit = null,
            initialCategory = defaultPwdCategory,
            initialPlainPassword = "",
            initialDecryptedNotes = "",
            onDismiss = { showAddPwdDialog = false },
            onSave = { title, category, username, accountId, plainPwd, notes ->
                viewModel.addPassword(title, category, username, accountId, plainPwd, notes) { success, _ ->
                    if (success) showAddPwdDialog = false
                }
            }
        )
    }

    // Edit Password Dialog
    if (pwdToEdit != null) {
        val currentPwd = pwdToEdit!!
        val plain = remember(currentPwd) { viewModel.decryptPassword(currentPwd) }
        val decryptedNotes = remember(currentPwd) { viewModel.decryptNotes(currentPwd.encryptedNotes) }
        AddEditPasswordDialog(
            passwordToEdit = currentPwd,
            initialPlainPassword = plain,
            initialDecryptedNotes = decryptedNotes,
            onDismiss = { pwdToEdit = null },
            onSave = { title, category, username, accountId, plainPwd, notes ->
                viewModel.updatePassword(currentPwd.id, title, category, username, accountId, plainPwd, notes) { success, _ ->
                    if (success) {
                        pwdToEdit = null
                        selectedPwd = null
                    }
                }
            }
        )
    }

    // Password Detail Dialog
    if (selectedPwd != null) {
        val currentPwd = selectedPwd!!
        val plain = remember(currentPwd) { viewModel.decryptPassword(currentPwd) }
        val decryptedNotes = remember(currentPwd) { viewModel.decryptNotes(currentPwd.encryptedNotes) }
        PasswordDetailDialog(
            password = currentPwd,
            plainPassword = plain,
            decryptedNotes = decryptedNotes,
            onDismiss = { selectedPwd = null },
            onCopyPassword = {
                val isHighRisk = currentPwd.category.equals("Bank", true) || currentPwd.category.equals("ATM", true)
                viewModel.copyToClipboard(currentPwd.title, it, isHighRisk)
            },
            onCopyUsername = {
                viewModel.copyToClipboard("Username", it, false)
            },
            onEdit = {
                pwdToEdit = currentPwd
            },
            onDelete = {
                pwdToDelete = currentPwd
            }
        )
    }

    // Delete Password Confirm Dialog
    if (pwdToDelete != null) {
        val pwd = pwdToDelete!!
        ConfirmDeleteDialog(
            title = "Delete Password Entry",
            message = "Are you sure you want to permanently delete credentials for “${pwd.title}”?",
            confirmButtonText = "Delete",
            onDismiss = { pwdToDelete = null },
            onConfirm = {
                viewModel.deletePassword(pwd)
                selectedPwd = null
                pwdToDelete = null
            }
        )
    }

    // Password Generator Dialog
    if (showPasswordGeneratorDialog) {
        PasswordGeneratorDialog(
            onDismiss = { showPasswordGeneratorDialog = false },
            onCopyPassword = { pwd ->
                viewModel.copyToClipboard("Generated Password", pwd, true)
                showPasswordGeneratorDialog = false
            }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onChangePin = { oldPin, newPin, callback ->
                viewModel.changeMasterPin(oldPin, newPin, callback)
            }
        )
    }

    // Export Backup Dialog
    if (showExportBackupDialog) {
        ExportBackupDialog(
            onDismiss = { showExportBackupDialog = false },
            onPerformExport = { uri, passphrase, callback ->
                viewModel.exportBackup(uri, passphrase, callback)
            }
        )
    }

    // Import Backup Dialog
    if (showImportBackupDialog) {
        ImportBackupDialog(
            onDismiss = { showImportBackupDialog = false },
            onPerformImport = { uri, passphrase, callback ->
                viewModel.importBackup(uri, passphrase, callback)
            }
        )
    }

    // Wipe Vault Confirm Dialog
    if (showWipeVaultDialog) {
        ConfirmDeleteDialog(
            title = "Wipe All Vault Data",
            message = "DANGER: This will permanently delete all encrypted documents, saved passwords, Master PIN, and security keys stored on this device. This action CANNOT be undone.",
            confirmButtonText = "Wipe Everything",
            isDestructiveWipe = true,
            onDismiss = { showWipeVaultDialog = false },
            onConfirm = {
                viewModel.wipeVault {
                    showWipeVaultDialog = false
                }
            }
        )
    }

    // Give Tip & Support Dialog
    if (showTipDialog) {
        GiveTipDialog(
            onDismiss = { showTipDialog = false }
        )
    }
}
