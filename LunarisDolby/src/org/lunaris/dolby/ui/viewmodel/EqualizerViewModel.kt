/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.data.autoeq.*
import org.lunaris.dolby.domain.models.*
import org.lunaris.dolby.utils.ToastHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DolbyRepository(application)
    private val context = application
    
    private val prefs: SharedPreferences = application.getSharedPreferences("autoeq_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<EqualizerUiState>(EqualizerUiState.Loading)
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private var currentProfile = 0
    private var currentBandMode = BandMode.TEN_BAND
    private var profileChangeJob: Job? = null
    private var isCleared = false

    private lateinit var autoEqRepository: AutoEqRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _currentAppliedAutoEqId = MutableStateFlow(prefs.getString("last_applied_id", "") ?: "")
    val currentAppliedAutoEqId: StateFlow<String> = _currentAppliedAutoEqId.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading = _isSearchLoading.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val filteredAutoEqList: StateFlow<List<IndexEntry>> = _searchQuery
        .debounce(250L)
        .map { query -> 
            if (::autoEqRepository.isInitialized) autoEqRepository.search(query) else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        DolbyConstants.dlog(TAG, "ViewModel initialized")
        loadEqualizer()
        observeProfileChanges()
    }
    
    private fun observeProfileChanges() {
        profileChangeJob?.cancel()
        profileChangeJob = viewModelScope.launch {
            repository.currentProfile.collect {
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Profile changed, reloading equalizer")
                    loadEqualizer()
                }
            }
        }
    }

    fun loadEqualizer() {
        if (isCleared) return
        
        viewModelScope.launch {
            try {
                currentProfile = repository.getCurrentProfile()
                currentBandMode = repository.getBandMode()
                val bandGains = repository.getEqualizerGains(currentProfile, currentBandMode)
                
                val builtInPresets = getBuiltInPresets(currentBandMode)
                val userPresets = repository.getUserPresets()
                val allPresets = userPresets + builtInPresets
                
                val currentPresetName = repository.getPresetName(currentProfile)
                
                if (currentPresetName.contains("AutoEQ", ignoreCase = true)) {
                    _currentAppliedAutoEqId.value = prefs.getString("last_applied_id", "") ?: ""
                } else {
                    _currentAppliedAutoEqId.value = ""
                    prefs.edit().putString("last_applied_id", "").commit()
                }
                
                val currentPreset = allPresets.find { it.name == currentPresetName }
                    ?: EqualizerPreset(
                        name = context.getString(R.string.dolby_preset_custom),
                        bandGains = bandGains,
                        isCustom = true,
                        bandMode = currentBandMode
                    )
                
                if (!isCleared) {
                    _uiState.value = EqualizerUiState.Success(
                        presets = allPresets,
                        currentPreset = currentPreset,
                        bandGains = bandGains,
                        bandMode = currentBandMode
                    )
                }
            } catch (e: Exception) {
                if (!isCleared) _uiState.value = EqualizerUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun initAutoEq(ctx: Context) {
        if (!::autoEqRepository.isInitialized) {
            autoEqRepository = AutoEqRepository(ctx.applicationContext)
        }
        viewModelScope.launch {
            _isSearchLoading.value = true
            autoEqRepository.initialize()
            _searchQuery.value = _searchQuery.value
            _isSearchLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun applyAutoEqProfileNetwork(ctx: Context, entry: IndexEntry) {
        viewModelScope.launch {
            _isSearchLoading.value = true
            val profile = autoEqRepository.getProfile(entry.id)
            
            if (profile != null) {
                prefs.edit().putString("last_applied_id", entry.id).commit()
                _currentAppliedAutoEqId.value = entry.id
                
                applyAutoEqProfile(profile.name, profile.graphicEq)
            } else {
                ToastHelper.showToast(ctx, "Failed to download profile for ${entry.name}")
            }
            _isSearchLoading.value = false
        }
    }

    fun applyAutoEqProfile(headphoneName: String, autoEqString: String) {
        val state = _uiState.value
        if (state !is EqualizerUiState.Success) return

        viewModelScope.launch {
            try {
                val parsedAutoEq = parseAutoEqString(autoEqString)
                if (parsedAutoEq.isEmpty()) {
                    DolbyConstants.dlog(TAG, "Failed to parse AutoEQ string")
                    return@launch
                }

                val targetFreqs = when (currentBandMode) {
                    BandMode.TEN_BAND -> DolbyRepository.BAND_FREQUENCIES_10
                    BandMode.FIFTEEN_BAND -> DolbyRepository.BAND_FREQUENCIES_15
                    BandMode.TWENTY_BAND -> DolbyRepository.BAND_FREQUENCIES_20
                }

                val newBandGains = targetFreqs.map { targetHz ->
                    val calculatedGain = interpolateGainForFrequency(targetHz, parsedAutoEq)
                    BandGain(frequency = targetHz, gain = calculatedGain.coerceIn(-150, 150))
                }

                val presetName = context.getString(R.string.dolby_autoeq_preset_name, headphoneName)
                
                if (state.presets.any { it.name.equals(presetName, ignoreCase = true) }) {
                    repository.deleteUserPreset(presetName)
                }
                
                repository.addUserPreset(presetName, newBandGains, currentBandMode)
                repository.setEqualizerGains(currentProfile, newBandGains, currentBandMode)
                
                ToastHelper.showToast(context, context.getString(R.string.dolby_autoeq_applied, headphoneName))
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error applying AutoEQ profile: ${e.message}")
            }
        }
    }
    
    private fun parseAutoEqString(eqString: String): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        val cleanString = eqString.removePrefix("GraphicEQ:").trim()
        val pairs = cleanString.split(";")
        
        for (pair in pairs) {
            val parts = pair.trim().split(Regex("\\s+"))
            if (parts.size == 2) {
                val hz = parts[0].toIntOrNull()
                val db = parts[1].toFloatOrNull()
                if (hz != null && db != null) {
                    map[hz] = (db * 10).toInt()
                }
            }
        }
        return map
    }

    private fun interpolateGainForFrequency(targetHz: Int, autoEqData: Map<Int, Int>): Int {
        if (autoEqData.containsKey(targetHz)) {
            return autoEqData[targetHz]!!
        }

        val sortedFreqs = autoEqData.keys.sorted()
        val lowerHz = sortedFreqs.lastOrNull { it < targetHz }
        val upperHz = sortedFreqs.firstOrNull { it > targetHz }

        if (lowerHz == null) return autoEqData[upperHz] ?: 0
        if (upperHz == null) return autoEqData[lowerHz] ?: 0

        val lowerGain = autoEqData[lowerHz]!!
        val upperGain = autoEqData[upperHz]!!
        
        val ratio = (targetHz - lowerHz).toFloat() / (upperHz - lowerHz)
        return (lowerGain + ratio * (upperGain - lowerGain)).toInt()
    }

    private fun getBuiltInPresets(bandMode: BandMode): List<EqualizerPreset> {
        val names = context.resources.getStringArray(R.array.dolby_preset_entries)
        val values = context.resources.getStringArray(R.array.dolby_preset_values)
        
        return names.mapIndexed { index, name ->
            val gains = values[index].split(",").map { it.toInt() }
            
            val frequencies = when (bandMode) {
                BandMode.TEN_BAND -> DolbyRepository.BAND_FREQUENCIES_10
                BandMode.FIFTEEN_BAND -> DolbyRepository.BAND_FREQUENCIES_15
                BandMode.TWENTY_BAND -> DolbyRepository.BAND_FREQUENCIES_20
            }
            
            val tenBandGains = gains.filterIndexed { i, _ -> i % 2 == 0 }
            
            val targetGains = when (bandMode) {
                BandMode.TEN_BAND -> tenBandGains
                BandMode.FIFTEEN_BAND -> {
                    val result = mutableListOf<Int>()
                    result.add(tenBandGains[0])
                    result.add(tenBandGains[0])
                    result.add(tenBandGains[0])
                    result.add(tenBandGains[1])
                    result.add(tenBandGains[2])
                    result.add(tenBandGains[3])
                    result.add(tenBandGains[4])
                    result.add((tenBandGains[4] + tenBandGains[5]) / 2)
                    result.add(tenBandGains[5])
                    result.add((tenBandGains[5] + tenBandGains[6]) / 2)
                    result.add(tenBandGains[6])
                    result.add((tenBandGains[6] + tenBandGains[7]) / 2)
                    result.add(tenBandGains[7])
                    result.add((tenBandGains[7] + tenBandGains[8]) / 2)
                    result.add(tenBandGains[9])
                    result
                }
                BandMode.TWENTY_BAND -> gains
            }
            
            EqualizerPreset(
                name = name,
                bandGains = frequencies.mapIndexed { i, freq ->
                    BandGain(frequency = freq, gain = targetGains.getOrElse(i) { 0 })
                },
                bandMode = bandMode
            )
        }
    }

    fun setBandMode(mode: BandMode) {
        viewModelScope.launch {
            try {
                repository.setBandMode(mode)
                currentBandMode = mode
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting band mode: ${e.message}")
            }
        }
    }

    fun setPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            try {
                val targetGains = if (preset.bandMode != currentBandMode) {
                    convertPresetToBandMode(preset, currentBandMode)
                } else {
                    preset.bandGains
                }
                
                repository.setEqualizerGains(currentProfile, targetGains, currentBandMode)
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting preset: ${e.message}")
            }
        }
    }

    private fun convertPresetToBandMode(preset: EqualizerPreset, targetMode: BandMode): List<BandGain> {
        val sourceFreqs = when (preset.bandMode) {
            BandMode.TEN_BAND -> DolbyRepository.BAND_FREQUENCIES_10
            BandMode.FIFTEEN_BAND -> DolbyRepository.BAND_FREQUENCIES_15
            BandMode.TWENTY_BAND -> DolbyRepository.BAND_FREQUENCIES_20
        }
        
        val targetFreqs = when (targetMode) {
            BandMode.TEN_BAND -> DolbyRepository.BAND_FREQUENCIES_10
            BandMode.FIFTEEN_BAND -> DolbyRepository.BAND_FREQUENCIES_15
            BandMode.TWENTY_BAND -> DolbyRepository.BAND_FREQUENCIES_20
        }
        
        if (preset.bandGains.size != sourceFreqs.size) {
            DolbyConstants.dlog(TAG, 
                "Preset band count mismatch: expected ${sourceFreqs.size}, got ${preset.bandGains.size}")
            return targetFreqs.map { BandGain(frequency = it, gain = 0) }
        }

        return targetFreqs.map { targetFreq ->
            val closestIdx = sourceFreqs.indexOfFirst { it >= targetFreq }
            val gain = when {
                closestIdx == -1 -> {
                    preset.bandGains.lastOrNull()?.gain ?: 0
                }
                closestIdx == 0 -> {
                    preset.bandGains.firstOrNull()?.gain ?: 0
                }
                else -> {
                    val prevFreq = sourceFreqs[closestIdx - 1]
                    val nextFreq = sourceFreqs[closestIdx]
                    val prevGain = preset.bandGains.getOrNull(closestIdx - 1)?.gain ?: 0
                    val nextGain = preset.bandGains.getOrNull(closestIdx)?.gain ?: 0
                    
                    val ratio = (targetFreq - prevFreq).toFloat() / (nextFreq - prevFreq)
                    (prevGain + ratio * (nextGain - prevGain)).toInt()
                }
            }
            BandGain(frequency = targetFreq, gain = gain)
        }
    }

    fun canEditCurrentPreset(): Boolean {
        val state = _uiState.value
        if (state is EqualizerUiState.Success) {
            return state.currentPreset.bandMode == currentBandMode
        }
        return false
    }

    fun getCurrentPresetBandMode(): BandMode? {
        val state = _uiState.value
        if (state is EqualizerUiState.Success) {
            return state.currentPreset.bandMode
        }
        return null
    }

    fun setBandGain(index: Int, gain: Int) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state is EqualizerUiState.Success) {
                    val isFlatPreset = state.currentPreset.name == context.getString(R.string.dolby_preset_default)
                    if (!isFlatPreset && state.currentPreset.bandMode != currentBandMode) {
                        ToastHelper.showToast(
                            context,
                            "Cannot edit ${state.currentPreset.bandMode.displayName} preset in ${currentBandMode.displayName} mode. " +
                            "Switch to ${state.currentPreset.bandMode.displayName} or select a different preset."
                        )
                        return@launch
                    }
                    val newBandGains = state.bandGains.toMutableList()
                    newBandGains[index] = newBandGains[index].copy(gain = gain)
                    repository.setEqualizerGains(currentProfile, newBandGains, currentBandMode)
                    loadEqualizer()
                }
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting band gain: ${e.message}")
            }
        }
    }

    fun savePreset(name: String): String? {
        val state = _uiState.value
        if (state !is EqualizerUiState.Success) return "Invalid state"
        
        if (state.presets.any { it.name.equals(name.trim(), ignoreCase = true) }) {
            return context.getString(R.string.dolby_geq_preset_name_exists)
        }
        
        if (name.length > 50) {
            return context.getString(R.string.dolby_geq_preset_name_too_long)
        }
        
        viewModelScope.launch {
            try {
                repository.addUserPreset(name.trim(), state.bandGains, currentBandMode)
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error saving preset: ${e.message}")
            }
        }
        
        return null
    }

    fun deletePreset(preset: EqualizerPreset) {
        if (!preset.isUserDefined) return
        
        viewModelScope.launch {
            try {
                repository.deleteUserPreset(preset.name)
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error deleting preset: ${e.message}")
            }
        }
    }

    fun saveImportedPreset(preset: EqualizerPreset): String? {
        val state = _uiState.value
        if (state !is EqualizerUiState.Success) return "Invalid state"
        
        if (state.presets.any { it.name.equals(preset.name.trim(), ignoreCase = true) }) {
            return context.getString(R.string.dolby_geq_preset_name_exists)
        }
        
        if (preset.name.length > 50) {
            return context.getString(R.string.dolby_geq_preset_name_too_long)
        }
        
        viewModelScope.launch {
            try {
                repository.addUserPreset(
                    preset.name.trim(), 
                    preset.bandGains, 
                    preset.bandMode
                )
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error saving imported preset: ${e.message}")
            }
        }
        
        return null
    }

    fun resetGains() {
        viewModelScope.launch {
            try {
                val flatPreset = getBuiltInPresets(currentBandMode).first()
                repository.setEqualizerGains(currentProfile, flatPreset.bandGains, currentBandMode)
                loadEqualizer()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error resetting gains: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        isCleared = true
        viewModelScope.coroutineContext.cancelChildren()
        profileChangeJob?.cancel()
        profileChangeJob = null
        repository.close()
        super.onCleared()
    }
    
    companion object {
        private const val TAG = "EqualizerViewModel"
    }
}
