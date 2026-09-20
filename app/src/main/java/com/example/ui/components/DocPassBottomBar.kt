package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.VaultTab
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy950

@Composable
fun DocPassBottomBar(
    currentTab: VaultTab,
    onTabSelected: (VaultTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("docpass_bottom_nav"),
        windowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        val items = listOf(
            Triple(VaultTab.HOME, "Dashboard", Icons.Filled.Security to Icons.Outlined.Security),
            Triple(VaultTab.DOCUMENTS, "Documents", Icons.Filled.Folder to Icons.Outlined.Folder),
            Triple(VaultTab.PASSWORDS, "Passwords", Icons.Filled.Key to Icons.Outlined.Key),
            Triple(VaultTab.SETTINGS, "Settings", Icons.Filled.Settings to Icons.Outlined.Settings)
        )

        items.forEach { (tab, label, icons) ->
            val isSelected = currentTab == tab
            val (selectedIcon, unselectedIcon) = icons

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) selectedIcon else unselectedIcon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}
