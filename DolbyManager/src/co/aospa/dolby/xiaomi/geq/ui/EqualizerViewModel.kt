/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.aospa.dolby.xiaomi.geq.data.EqualizerRepository
import co.aospa.dolby.xiaomi.geq.data.Preset
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

const val TAG = "EqViewModel"

class EqualizerViewModel(
    private val repository: EqualizerRepository
) : ViewModel() {

    private val _presets = MutableStateFlow(repository.builtInPresets)
    val presets = _presets.asStateFlow()

    private val _preset = MutableStateFlow(repository.defaultPreset)
    val preset = _preset.asStateFlow()

    private var presetRestored = false

    init {
        // Update the list of presets: combined list of user defined presets if any,
        // and then the built in presets.
        repository.userPresets
            .onEach { presets ->
                dlog(TAG, "updated userPresets: $presets")
                _presets.value = mutableListOf<Preset>().apply {
                    addAll(presets)
                    addAll(repository.builtInPresets)
                }.toList()

                // We can restore the active preset only after the presets list is populated,
                // since we do not save the preset name but only its gains.
                if (!presetRestored) {
                    val bandGains = repository.getBandGains()
                    _preset.value = _presets.value.find {
                        bandGains == it.bandGains
                    } ?: Preset(
                        name = "Custom",
                        bandGains = bandGains
                    )
                    dlog(TAG, "restored preset: ${_preset.value}")
                    presetRestored = true
                }
            }
            .launchIn(viewModelScope)

        // Update the preset in repository everytime we set it here
        _preset
            .drop(1) // skip the initial value
            .onEach {
                // wait till the active preset is restored
                if (!presetRestored) {
                    return@onEach
                }
                dlog(TAG, "updated preset: $it")
                repository.setBandGains(it.bandGains)
                if (it.isUserDefined) {
                    repository.addPreset(it)
                }
            }
            .launchIn(viewModelScope)
    }

    fun reset() {
        dlog(TAG, "reset()")
        if (_preset.value.isUserDefined) {
            // Reset gains to 0
            _preset.value = _preset.value.copy(
                bandGains = repository.defaultPreset.bandGains
            )
        } else {
            // Switch to flat preset
            _preset.value = repository.defaultPreset
        }
    }

    fun setPreset(preset: Preset) {
        dlog(TAG, "setPreset($preset)")
        _preset.value = preset
    }

    fun setGain(index: Int, gain: Int) {
        dlog(TAG, "setGain($index, $gain)")
        _preset.value = _preset.value.run {
            copy(
                name = if (!isUserDefined) "Custom" else name,
                bandGains = bandGains
                    .toMutableList()
                    // create a new object to ensure the flow emits an update.
                    .apply { this[index] = this[index].copy(gain = gain) }
                    .toList(),
                isMutated = true
            )
        }
    }

    // Returns string containing the error message if it failed, otherwise null
    private fun validatePresetName(name: String): PresetNameValidationError? {
        // Ensure we don't have another preset with the same name
        return if (
            _presets.value
            .any { it.name.equals(name.trim(), ignoreCase = true) }
        ) {
            PresetNameValidationError.NAME_EXISTS
        } else if (name.length > 50) {
            PresetNameValidationError.NAME_TOO_LONG
        } else null
    }

    fun createNewPreset(name: String): PresetNameValidationError? {
        dlog(TAG, "createNewPreset($name)")
        validatePresetName(name)?.let {
            dlog(TAG, "createNewPreset failed: $it")
            return it
        }
        _preset.value = _preset.value.copy(
            name = name.trim(),
            isUserDefined = true,
            isMutated = false
        )
        return null
    }

    fun renamePreset(preset: Preset, name: String): PresetNameValidationError? {
        dlog(TAG, "renamePreset($preset, $name)")
        // create a preset with the new name and same gains
        createNewPreset(name = name)?.let {
            dlog(TAG, "renamePreset failed")
            return it
        }
        // and delete the old one.
        deletePreset(preset, shouldReset = false)
        return null
    }

    fun deletePreset(preset: Preset, shouldReset: Boolean = true) {
        dlog(TAG, "deletePreset($preset)")
        viewModelScope.launch {
            repository.removePreset(preset)
        }
        if (shouldReset) {
            _preset.value = repository.defaultPreset
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EqualizerViewModel(
                    repository = EqualizerRepository(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    )
                )
            }
        }
    }
}
