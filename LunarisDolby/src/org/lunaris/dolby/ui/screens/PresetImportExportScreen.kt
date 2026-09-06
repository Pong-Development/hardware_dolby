/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.lunaris.dolby.R
import org.lunaris.dolby.data.PresetExportManager
import org.lunaris.dolby.domain.models.EqualizerPreset
import org.lunaris.dolby.domain.models.EqualizerUiState
import org.lunaris.dolby.ui.components.ModernConfirmDialog
import org.lunaris.dolby.ui.viewmodel.EqualizerViewModel
import org.lunaris.dolby.utils.ToastHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PresetImportExportScreen(
    viewModel: EqualizerViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportManager = remember { PresetExportManager(context) }
    val uiState by viewModel.uiState.collectAsState()
    var selectedPreset by remember { mutableStateOf<EqualizerPreset?>(null) }
    var showExportOptions by remember { mutableStateOf(false) }
    var showBatchExport by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<EqualizerPreset?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            selectedPreset?.let { preset ->
                scope.launch {
                    isLoading = true
                    exportManager.exportPresetToFile(preset, uri).fold(
                        onSuccess = {
                            ToastHelper.showToast(context, "Preset exported successfully!")
                        },
                        onFailure = { e ->
                            ToastHelper.showToast(context, "Export failed: ${e.message}")
                        }
                    )
                    isLoading = false
                    showExportOptions = false
                }
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isLoading = true
                exportManager.importPresetFromFile(uri).fold(
                    onSuccess = { preset ->
                        val error = viewModel.saveImportedPreset(preset)
                        if (error != null) {
                            ToastHelper.showToast(context, error)
                        } else {
                            ToastHelper.showToast(
                                context, 
                                "Preset '${preset.name}' imported! (${preset.bandMode.displayName})"
                            )
                            viewModel.loadEqualizer()
                        }
                    },
                    onFailure = { e ->
                        ToastHelper.showToast(context, "Import failed: ${e.message}")
                    }
                )
                isLoading = false
            }
        }
    }
    
    val batchExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isLoading = true
                val state = uiState as? EqualizerUiState.Success
                val presets = state?.presets?.filter { it.isUserDefined } ?: emptyList()
                
                exportManager.exportMultiplePresets(presets, uri).fold(
                    onSuccess = {
                        ToastHelper.showToast(context, "${presets.size} presets exported!")
                    },
                    onFailure = { e ->
                        ToastHelper.showToast(context, "Batch export failed: ${e.message}")
                    }
                )
                isLoading = false
                showBatchExport = false
            }
        }
    }
    
    val batchImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isLoading = true
                exportManager.importMultiplePresets(uri).fold(
                    onSuccess = { presets ->
                        var successCount = 0
                        presets.forEach { preset ->
                            if (viewModel.saveImportedPreset(preset) == null) {
                                successCount++
                            }
                        }
                        ToastHelper.showToast(
                            context, 
                            "Imported $successCount of ${presets.size} presets"
                        )
                        viewModel.loadEqualizer()
                    },
                    onFailure = { e ->
                        ToastHelper.showToast(context, "Batch import failed: ${e.message}")
                    }
                )
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.import_export_presets),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showBatchExport = true }) {
                        Icon(
                            Icons.Default.FileDownload, 
                            contentDescription = "Batch export",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = uiState) {
                is EqualizerUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.FileDownload,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            stringResource(R.string.import_presets),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.import_presets_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { importLauncher.launch("*/*") },
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.large,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.import_single_file))
                                        }
                                        Button(
                                            onClick = { batchImportLauncher.launch("*/*") },
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.large,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Default.FolderCopy, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.import_batch))
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                exportManager.importPresetFromClipboard().fold(
                                                    onSuccess = { preset ->
                                                        val error = viewModel.saveImportedPreset(preset)
                                                        if (error != null) {
                                                            ToastHelper.showToast(context, error)
                                                        } else {
                                                            ToastHelper.showToast(
                                                                context, 
                                                                "Preset imported from clipboard! (${preset.bandMode.displayName})"
                                                            )
                                                            viewModel.loadEqualizer()
                                                        }
                                                    },
                                                    onFailure = { e ->
                                                        ToastHelper.showToast(
                                                            context, 
                                                            "Clipboard import failed: ${e.message}"
                                                        )
                                                    }
                                                )
                                                isLoading = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.import_from_clipboard))
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.your_custom_presets),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(state.presets.filter { it.isUserDefined }) { preset ->
                            PresetExportCard(
                                preset = preset,
                                onExportFile = {
                                    selectedPreset = preset
                                    exportLauncher.launch("${preset.name.replace(" ", "_")}_${preset.bandMode.value}band.ldp")
                                },
                                onCopyClipboard = {
                                    scope.launch {
                                        isLoading = true
                                        exportManager.copyPresetToClipboard(preset).fold(
                                            onSuccess = {
                                                ToastHelper.showToast(
                                                    context, 
                                                    "Preset copied to clipboard!"
                                                )
                                            },
                                            onFailure = { e ->
                                                ToastHelper.showToast(
                                                    context, 
                                                    "Copy failed: ${e.message}"
                                                )
                                            }
                                        )
                                        isLoading = false
                                    }
                                },
                                onShare = {
                                    scope.launch {
                                        isLoading = true
                                        exportManager.createShareIntent(preset).fold(
                                            onSuccess = { intent ->
                                                context.startActivity(intent)
                                            },
                                            onFailure = { e ->
                                                ToastHelper.showToast(
                                                    context, 
                                                    "Share failed: ${e.message}"
                                                )
                                            }
                                        )
                                        isLoading = false
                                    }
                                },
                                onDelete = {
                                    presetToDelete = preset
                                    showDeleteDialog = true
                                }
                            )
                        }
                        item {
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.processing),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showBatchExport) {
        val state = uiState as? EqualizerUiState.Success
        val presetCount = state?.presets?.count { it.isUserDefined } ?: 0
        
        AlertDialog(
            onDismissRequest = { showBatchExport = false },
            icon = {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            title = {
                Text(
                    stringResource(R.string.batch_export),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    stringResource(R.string.batch_export_description, presetCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        batchExportLauncher.launch("dolby_presets_backup.ldp")
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.export_all))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchExport = false },
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    if (showDeleteDialog && presetToDelete != null) {
        ModernConfirmDialog(
            title = "Delete Preset",
            message = "Are you sure you want to delete '${presetToDelete!!.name}'? This will remove it from your preset list and cannot be undone.",
            icon = Icons.Default.Delete,
            onConfirm = {
                scope.launch {
                    viewModel.deletePreset(presetToDelete!!)
                    ToastHelper.showToast(context, "Preset '${presetToDelete!!.name}' deleted")
                    viewModel.loadEqualizer()
                    showDeleteDialog = false
                    presetToDelete = null
                }
            },
            onDismiss = {
                showDeleteDialog = false
                presetToDelete = null
            }
        )
    }
}

@Composable
private fun PresetExportCard(
    preset: EqualizerPreset,
    onExportFile: () -> Unit,
    onCopyClipboard: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${preset.bandMode.displayName} • ${preset.bandGains.size} bands",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onExportFile) {
                        Icon(
                            Icons.Default.FileDownload, 
                            contentDescription = "Export to file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onCopyClipboard) {
                        Icon(
                            Icons.Default.ContentCopy, 
                            contentDescription = "Copy to clipboard",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Default.Share, 
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
