/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.lunaris.dolby.R
import org.lunaris.dolby.domain.models.DolbyUiState
import org.lunaris.dolby.ui.components.*
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModernDolbySettingsScreen(
    viewModel: DolbyViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(null)
    
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.dolby_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                actions = {
                    IconButton(onClick = { showCreditsDialog = true }) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "Credits",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            Icons.Default.RestartAlt, 
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DolbyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is DolbyUiState.Success -> {
                ModernDolbySettingsContent(
                    state = state,
                    viewModel = viewModel,
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is DolbyUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        }

    }
    if (showResetDialog) {
        ModernConfirmDialog(
            title = stringResource(R.string.dolby_reset_all),
            message = stringResource(R.string.dolby_reset_all_message),
            icon = Icons.Default.RestartAlt,
            onConfirm = {
                viewModel.resetAllProfiles()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }
    
    if (showCreditsDialog) {
        CreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }
}

@Composable
private fun ModernDolbySettingsContent(
    state: DolbyUiState.Success,
    viewModel: DolbyViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DolbyMainCard(
                enabled = state.settings.enabled,
                onEnabledChange = { viewModel.setDolbyEnabled(it) }
            )
        }

        item {
            NotificationListenerPermissionCard()
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModernProfileSelector(
                    currentProfile = state.settings.currentProfile,
                    onProfileChange = { viewModel.setProfile(it) }
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled && state.settings.currentProfile != 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModernSettingsCard(
                    title = "Intelligent Equalizer",
                    icon = Icons.Default.GraphicEq
                ) {
                    ModernIeqSelector(
                        currentPreset = state.profileSettings.ieqPreset,
                        onPresetChange = { viewModel.setIeqPreset(it) }
                    )
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = state.settings.enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AppProfileSettingsCard(
                    onManageClick = { navController.navigate("app_profiles") }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}
