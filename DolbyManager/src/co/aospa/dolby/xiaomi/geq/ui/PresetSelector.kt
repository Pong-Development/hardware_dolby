/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSelector(viewModel: EqualizerViewModel) {
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.preset.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showNewPresetDialog by remember { mutableStateOf(false) }
    var showRenamePresetDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .padding(end = 8.dp)
                .weight(1f)
        ) {
            TextField(
                value = currentPreset.name,
                onValueChange = { },
                readOnly = true,
                label = {
                    Text(
                        stringResource(id = R.string.dolby_geq_preset)
                    )
                },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier.menuAnchor()
                    // prevent keyboard from popping up
                    .focusProperties { canFocus = false }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(text = preset.name) },
                        onClick = {
                            viewModel.setPreset(preset)
                            expanded = false
                        }
                    )
                }
            }
        }

        TooltipIconButton(
            icon = ImageVector.vectorResource(
                id = R.drawable.save_as_24px
            ),
            text = stringResource(id = R.string.dolby_geq_new_preset),
            onClick = { showNewPresetDialog = true }
        )

        if (currentPreset.isUserDefined) {
            TooltipIconButton(
                icon = Icons.Default.Edit,
                text = stringResource(id = R.string.dolby_geq_rename_preset),
                onClick = { showRenamePresetDialog = true }
            )
            TooltipIconButton(
                icon = Icons.Default.Delete,
                text = stringResource(id = R.string.dolby_geq_delete_preset),
                onClick = { showDeleteConfirmDialog = true }
            )
        }

        TooltipIconButton(
            icon = ImageVector.vectorResource(
                id = R.drawable.reset_settings_24px
            ),
            text = stringResource(id = R.string.dolby_geq_reset_gains),
            onClick = {
                if (currentPreset.isUserDefined) {
                    showResetConfirmDialog = true
                } else {
                    viewModel.reset()
                }
            }
        )
    }

    // Dialogs

    if (showNewPresetDialog) {
        PresetNameDialog(
            title = stringResource(id = R.string.dolby_geq_new_preset),
            onPresetNameSet = {
                return@PresetNameDialog viewModel.createNewPreset(name = it)
            },
            onDismissDialog = { showNewPresetDialog = false }
        )
    }

    if (showRenamePresetDialog) {
        PresetNameDialog(
            title = stringResource(id = R.string.dolby_geq_rename_preset),
            presetName = currentPreset.name,
            onPresetNameSet = {
                return@PresetNameDialog viewModel.renamePreset(
                    preset = currentPreset,
                    name = it
                )
            },
            onDismissDialog = { showRenamePresetDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        ConfirmationDialog(
            text = stringResource(id = R.string.dolby_geq_delete_preset_prompt),
            onConfirm = { viewModel.deletePreset(currentPreset) },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    if (showResetConfirmDialog) {
        ConfirmationDialog(
            text = stringResource(id = R.string.dolby_geq_reset_gains_prompt),
            onConfirm = { viewModel.reset() },
            onDismiss = { showResetConfirmDialog = false }
        )
    }
}
