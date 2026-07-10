/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.lunaris.dolby.R
import org.lunaris.dolby.data.autoeq.*
import org.lunaris.dolby.ui.components.*
import org.lunaris.dolby.ui.viewmodel.EqualizerViewModel
import org.lunaris.dolby.domain.models.*
import org.lunaris.dolby.utils.*

enum class EqualizerViewMode {
    CURVE,
    SLIDERS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModernEqualizerScreen(
    viewModel: EqualizerViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAutoEqDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(EqualizerViewMode.CURVE) }
    val currentRoute by navController.currentBackStackEntryFlow.collectAsState(null)
    
    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.dolby_preset),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                actions = {
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            Icons.Default.Save, 
                            contentDescription = "Save",
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
                    IconButton(onClick = { navController.navigate("import_export") }) {
                        Icon(
                            Icons.Default.ImportExport, 
                            contentDescription = "Import/Export",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showAutoEqDialog = true }) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "AutoEQ",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (uiState is EqualizerUiState.Success) {
                        val state = uiState as EqualizerUiState.Success
                        if (state.currentPreset.isUserDefined) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is EqualizerUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is EqualizerUiState.Success -> {
                ModernEqualizerContent(
                    state = state,
                    viewModel = viewModel,
                    viewMode = viewMode,
                    onViewModeChange = { viewMode = it },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is EqualizerUiState.Error -> {
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

    if (showSaveDialog) {
        SavePresetDialog(
            onSave = { name ->
                val error = viewModel.savePreset(name)
                if (error == null) {
                    showSaveDialog = false
                }
                error
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showDeleteDialog && uiState is EqualizerUiState.Success) {
        val state = uiState as EqualizerUiState.Success
        ModernConfirmDialog(
            title = stringResource(R.string.dolby_geq_delete_preset),
            message = stringResource(R.string.dolby_geq_delete_preset_prompt),
            icon = Icons.Default.Delete,
            onConfirm = {
                viewModel.deletePreset(state.currentPreset)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showResetDialog) {
        ModernConfirmDialog(
            title = stringResource(R.string.dolby_geq_reset_gains),
            message = stringResource(R.string.dolby_geq_reset_gains_prompt),
            icon = Icons.Default.RestartAlt,
            onConfirm = {
                viewModel.resetGains()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showAutoEqDialog) {
        AutoEqSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showAutoEqDialog = false }
        )
    }
}

@Composable
private fun ModernEqualizerContent(
    state: EqualizerUiState.Success,
    viewModel: EqualizerViewModel,
    viewMode: EqualizerViewMode,
    onViewModeChange: (EqualizerViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFlatPreset = state.currentPreset.name == stringResource(R.string.dolby_preset_default)
    val scrollState = rememberScrollState()
    val isBandModeCompatible = state.currentPreset.bandMode == state.bandMode
    val canEdit = isBandModeCompatible || isFlatPreset
    val isActive = canEdit && !isFlatPreset
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            ModernPresetSelector(
                presets = state.presets,
                currentPreset = state.currentPreset,
                onPresetSelected = { viewModel.setPreset(it) }
            )
        }
        
        BandModeSelector(
            currentMode = state.bandMode,
            onModeChange = { viewModel.setBandMode(it) }
        )
        
        if (!isBandModeCompatible && !isFlatPreset) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.band_mode_mismatch),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This ${state.currentPreset.bandMode.displayName} preset cannot be edited in ${state.bandMode.displayName} mode. " +
                                  "Switch to ${state.currentPreset.bandMode.displayName} or select a compatible preset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Equalizer View",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViewModeTile(
                        title = "Curve",
                        icon = Icons.Default.ShowChart,
                        isSelected = viewMode == EqualizerViewMode.CURVE,
                        onClick = { onViewModeChange(EqualizerViewMode.CURVE) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    ViewModeTile(
                        title = "Sliders",
                        icon = Icons.Default.Tune,
                        isSelected = viewMode == EqualizerViewMode.SLIDERS,
                        onClick = { onViewModeChange(EqualizerViewMode.SLIDERS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = viewMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                fadeOut(animationSpec = tween(300))
            },
            label = "equalizer_view_transition"
        ) { mode ->
            when (mode) {
                EqualizerViewMode.CURVE -> {
                    CurveViewContent(
                        state = state,
                        viewModel = viewModel,
                        canEdit = canEdit,
                        isActive = isActive
                    )
                }
                EqualizerViewMode.SLIDERS -> {
                    SlidersViewContent(
                        state = state,
                        viewModel = viewModel,
                        canEdit = canEdit
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
private fun CurveViewContent(
    state: EqualizerUiState.Success,
    viewModel: EqualizerViewModel,
    canEdit: Boolean,
    isActive: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (canEdit) "Interactive Frequency Response" 
                          else "Frequency Response (Read-only)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canEdit) MaterialTheme.colorScheme.onSurface
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (canEdit) MaterialTheme.colorScheme.secondaryContainer
                          else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "${state.bandMode.bandCount} bands",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canEdit) MaterialTheme.colorScheme.onSecondaryContainer
                              else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = if (canEdit) 
                    "Drag the control points to adjust gain (±15 dB) • ${getFrequencyRange(state.bandMode)}"
                else
                    "Read-only view • Band mode mismatch",
                style = MaterialTheme.typography.bodySmall,
                color = if (canEdit) MaterialTheme.colorScheme.onSurfaceVariant
                      else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            InteractiveFrequencyResponseCurve(
                bandGains = state.bandGains,
                onBandGainChange = { index, newGain ->
                    if (canEdit) {
                        viewModel.setBandGain(index, newGain)
                    }
                },
                isActive = isActive,
                isEditable = canEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun SlidersViewContent(
    state: EqualizerUiState.Success,
    viewModel: EqualizerViewModel,
    canEdit: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.frequency_response),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = getFrequencyRange(state.bandMode),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FrequencyResponseCurve(
                    bandGains = state.bandGains,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (canEdit) stringResource(R.string.dolby_geq_slider_label_gain)
                              else "${stringResource(R.string.dolby_geq_slider_label_gain)} (Read-only)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canEdit) MaterialTheme.colorScheme.onSurface
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (!canEdit) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.locked),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.bandGains) { index, bandGain ->
                        ModernEqualizerBand(
                            frequency = bandGain.frequency,
                            gain = bandGain.gain,
                            onGainChange = { newGain ->
                                if (canEdit) {
                                    viewModel.setBandGain(index, newGain)
                                }
                            },
                            enabled = canEdit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getFrequencyRange(bandMode: BandMode): String {
    return when (bandMode) {
        BandMode.TEN_BAND -> "32Hz - 19.7kHz"
        BandMode.FIFTEEN_BAND -> "32Hz - 19.7kHz"
        BandMode.TWENTY_BAND -> "32Hz - 19.7kHz"
    }
}

@Composable
private fun ViewModeTile(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    
    Surface(
        onClick = {
            scope.launch {
                haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
            }
            onClick()
        },
        modifier = modifier
            .height(72.dp)
            .squishable(enabled = true, scaleDown = 0.93f),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = if (isSelected)
            MaterialTheme.shapes.extraLarge
        else
            MaterialTheme.shapes.large,
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = if (isSelected)
                    MaterialTheme.shapes.extraLarge
                else
                    MaterialTheme.shapes.medium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BandModeSelector(
    currentMode: BandMode,
    onModeChange: (BandMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.band_configuration),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Text(
                text = stringResource(R.string.choose_equalizer_precision),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BandMode.values().forEach { mode ->
                    BandModeTile(
                        mode = mode,
                        isSelected = currentMode == mode,
                        onClick = {
                            scope.launch {
                                haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
                            }
                            onModeChange(mode)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BandModeTile(
    mode: BandMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    
    Surface(
        onClick = {
            scope.launch {
                haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
            }
            onClick()
        },
        modifier = modifier
            .height(80.dp)
            .squishable(enabled = true, scaleDown = 0.93f),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = if (isSelected)
            MaterialTheme.shapes.extraLarge
        else
            MaterialTheme.shapes.large,
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = if (isSelected)
                    MaterialTheme.shapes.extraLarge
                else
                    MaterialTheme.shapes.small,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = mode.value,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernPresetSelector(
    presets: List<EqualizerPreset>,
    currentPreset: EqualizerPreset,
    onPresetSelected: (EqualizerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.padding(20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dolby_geq_preset),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { 
                scope.launch {
                    haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.TICK)
                }
                expanded = it 
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (currentPreset.isUserDefined) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = currentPreset.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    preset.name,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (preset.isUserDefined) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        onClick = {
                            scope.launch {
                                haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
                            }
                            onPresetSelected(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ModernEqualizerBand(
    frequency: Int,
    gain: Int,
    onGainChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var sliderValue by remember(gain) { mutableFloatStateOf(gain / 10f) }
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    var lastHapticValue by remember { mutableIntStateOf((gain / 10f).toInt()) }

    Column(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (enabled) MaterialTheme.colorScheme.primaryContainer
                   else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "%.1f".format(sliderValue),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                if (enabled) {
                    val intValue = (newValue * 10).toInt() / 10
                    if (intValue != lastHapticValue) {
                        scope.launch {
                            haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.TEXTURE_TICK)
                        }
                        lastHapticValue = intValue
                    }
                    sliderValue = newValue
                }
            },
            onValueChangeFinished = {
                if (enabled) {
                    onGainChange((sliderValue * 10).toInt())
                }
            },
            enabled = enabled,
            valueRange = -15f..15f,
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxHeight,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
                .weight(1f)
                .width(48.dp),
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                activeTrackColor = if (enabled) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.outline,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.outline,
                disabledActiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            text = if (frequency >= 1000) "${frequency / 1000}k" else "$frequency",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FrequencyResponseCurve(
    bandGains: List<BandGain>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    
    Canvas(modifier = modifier.background(surfaceColor.copy(alpha = 0.3f))) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        drawLine(
            color = surfaceColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 2f
        )
        
        for (i in 1..4) {
            val y = (height / 5) * i
            drawLine(
                color = surfaceColor.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }
        
        if (bandGains.isNotEmpty()) {
            val path = Path()
            val stepX = width / (bandGains.size - 1)
            
            bandGains.forEachIndexed { index, bandGain ->
                val x = index * stepX
                val normalizedGain = (bandGain.gain / 150f).coerceIn(-1f, 1f)
                val y = centerY - (normalizedGain * centerY * 0.8f)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevGain = bandGains[index - 1].gain
                    val prevNormalizedGain = (prevGain / 150f).coerceIn(-1f, 1f)
                    val prevY = centerY - (prevNormalizedGain * centerY * 0.8f)
                    
                    val cpX1 = prevX + stepX * 0.4f
                    val cpY1 = prevY
                    val cpX2 = x - stepX * 0.4f
                    val cpY2 = y
                    
                    path.cubicTo(cpX1, cpY1, cpX2, cpY2, x, y)
                }
            }
            
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 4f)
            )
            
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.3f),
                        primaryColor.copy(alpha = 0.05f)
                    )
                )
            )
        }
    }
}

@Composable
private fun SavePresetDialog(
    onSave: (String) -> String?,
    onDismiss: () -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { 
            Text(
                stringResource(R.string.dolby_geq_new_preset),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ) 
        },
        text = {
            Column {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { 
                        presetName = it
                        errorMessage = null
                    },
                    label = { 
                        Text(
                            stringResource(R.string.dolby_geq_preset_name),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = onSave(presetName)
                    if (error != null) {
                        errorMessage = error
                    }
                },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    stringResource(android.R.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoEqSelectionDialog(
    viewModel: EqualizerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initAutoEq(context)
        viewModel.updateSearchQuery("")
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredList by viewModel.filteredAutoEqList.collectAsState()
    val isLoading by viewModel.isSearchLoading.collectAsState()
    val activeAutoEqId by viewModel.currentAppliedAutoEqId.collectAsState()
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.dolby_autoeq_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text(stringResource(R.string.dolby_autoeq_search_hint)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.dolby_autoeq_clear))
                                }
                            } else {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.dolby_autoeq_search))
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                    if (searchQuery.isEmpty() && !activeAutoEqId.isNullOrEmpty()) {
                    val activeEntry = filteredList.find { it.id == activeAutoEqId }
                    if (activeEntry != null) {
                        Text(
                            text = stringResource(R.string.dolby_autoeq_currently_applied),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = activeEntry.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(text = "${activeEntry.source} • ${activeEntry.measurementRig}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.dolby_autoeq_active), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (isLoading && filteredList.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { 
                        CircularProgressIndicator() 
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.id }) { entry ->
                            val isSelected = entry.id == activeAutoEqId

                            Surface(
                                onClick = {
                                    viewModel.applyAutoEqProfileNetwork(context, entry)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp), 
                                    horizontalArrangement = Arrangement.SpaceBetween, 
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name, 
                                            fontWeight = FontWeight.Bold, 
                                            style = MaterialTheme.typography.bodyMedium, 
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${entry.source} • ${entry.measurementRig}", 
                                            style = MaterialTheme.typography.labelSmall, 
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check, 
                                            contentDescription = stringResource(R.string.dolby_autoeq_selected), 
                                            tint = MaterialTheme.colorScheme.secondary, 
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
