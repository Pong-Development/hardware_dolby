/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.domain.models.*
import org.lunaris.dolby.service.DolbyEffectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

class DolbyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DolbyRepository(application)

    private val _uiState = MutableStateFlow<DolbyUiState>(DolbyUiState.Loading)
    val uiState: StateFlow<DolbyUiState> = _uiState.asStateFlow()
    val currentProfile: StateFlow<Int> = repository.currentProfile
    
    private var speakerStateJob: Job? = null
    private var profileChangeJob: Job? = null
    @Volatile private var isCleared = false

    init {
        DolbyConstants.dlog(TAG, "ViewModel initialized")
        loadSettings()
        observeSpeakerState()
        observeProfileChanges()
    }
    
    private fun observeSpeakerState() {
        speakerStateJob?.cancel()
        speakerStateJob = viewModelScope.launch {
            repository.isOnSpeaker.collect { 
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Speaker state changed: $it")
                    loadSettings()
                }
            }
        }
    }
    
    private fun observeProfileChanges() {
        profileChangeJob?.cancel()
        profileChangeJob = viewModelScope.launch {
            repository.currentProfile.collect {
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Profile changed to: $it")
                    loadSettings()
                }
            }
        }
    }

    fun loadSettings() {
        if (isCleared) {
            DolbyConstants.dlog(TAG, "ViewModel cleared, skipping loadSettings")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val enabled = repository.getDolbyEnabled()
                val profile = repository.getCurrentProfile()
                val bandMode = repository.getBandMode()
                
                val settings = DolbySettings(
                    enabled = enabled,
                    currentProfile = profile,
                    bassEnhancerEnabled = repository.getBassEnhancerEnabled(profile),
                    volumeLevelerEnabled = repository.getVolumeLevelerEnabled(profile),
                    bandMode = bandMode
                )
                
                val profileSettings = ProfileSettings(
                    profile = profile,
                    ieqPreset = repository.getIeqPreset(profile),
                    headphoneVirtualizerEnabled = repository.getHeadphoneVirtualizerEnabled(profile),
                    speakerVirtualizerEnabled = repository.getSpeakerVirtualizerEnabled(profile),
                    stereoWideningAmount = repository.getStereoWideningAmount(profile),
                    dialogueEnhancerEnabled = repository.getDialogueEnhancerEnabled(profile),
                    dialogueEnhancerAmount = repository.getDialogueEnhancerAmount(profile),
                    bassLevel = repository.getBassLevel(profile),
                    midLevel = repository.getMidLevel(profile),
                    trebleLevel = repository.getTrebleLevel(profile),
                    bassCurve = repository.getBassCurve(profile)
                )
                
                if (!isCleared) {
                    _uiState.value = DolbyUiState.Success(
                        settings = settings,
                        profileSettings = profileSettings,
                        currentPresetName = repository.getPresetName(profile),
                        isOnSpeaker = repository.isOnSpeaker.value
                    )
                }
            } catch (e: Exception) {
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Error loading settings: ${e.message}")
                    _uiState.value = DolbyUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun setDolbyEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.setDolbyEnabled(enabled)
                if (enabled) {
                    DolbyEffectService.start(getApplication())
                } else {
                    DolbyEffectService.stop(getApplication())
                }
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting Dolby enabled: ${e.message}")
            }
        }
    }

    fun setProfile(profile: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.setCurrentProfile(profile)
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting profile: ${e.message}")
            }
        }
    }

    fun setBassEnhancer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setBassEnhancerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting bass enhancer: ${e.message}")
            }
        }
    }

    fun setBassLevel(level: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setBassLevel(profile, level)
                loadSettings()
            } catch (e: IllegalArgumentException) {
                DolbyConstants.dlog(TAG, "Invalid bass level: ${e.message}")
                _uiState.value = DolbyUiState.Error("Invalid bass level: ${e.message}")
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting bass level: ${e.message}")
                _uiState.value = DolbyUiState.Error("Failed to set bass level")
            }
        }
    }

    fun setBassCurve(curve: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setBassCurve(profile, curve)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting bass curve: ${e.message}")
            }
        }
    }

    fun setMidLevel(level: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setMidLevel(profile, level)
                loadSettings()
            } catch (e: IllegalArgumentException) {
                DolbyConstants.dlog(TAG, "Invalid mid level: ${e.message}")
                _uiState.value = DolbyUiState.Error("Invalid mid level: ${e.message}")
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting mid level: ${e.message}")
                _uiState.value = DolbyUiState.Error("Failed to set mid level")
            }
        }
    }

    fun setTrebleLevel(level: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setTrebleLevel(profile, level)
                loadSettings()
            } catch (e: IllegalArgumentException) {
                DolbyConstants.dlog(TAG, "Invalid treble level: ${e.message}")
                _uiState.value = DolbyUiState.Error("Invalid treble level: ${e.message}")
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting treble level: ${e.message}")
                _uiState.value = DolbyUiState.Error("Failed to set treble level")
            }
        }
    }

    fun setVolumeLeveler(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setVolumeLevelerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting volume leveler: ${e.message}")
            }
        }
    }

    fun setIeqPreset(preset: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setIeqPreset(profile, preset)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting IEQ preset: ${e.message}")
            }
        }
    }

    fun setHeadphoneVirtualizer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setHeadphoneVirtualizerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting headphone virtualizer: ${e.message}")
            }
        }
    }

    fun setSpeakerVirtualizer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setSpeakerVirtualizerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting speaker virtualizer: ${e.message}")
            }
        }
    }

    fun setStereoWidening(amount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setStereoWideningAmount(profile, amount)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting stereo widening: ${e.message}")
            }
        }
    }

    fun setDialogueEnhancer(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setDialogueEnhancerEnabled(profile, enabled)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting dialogue enhancer: ${e.message}")
            }
        }
    }

    fun setDialogueEnhancerAmount(amount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = repository.getCurrentProfile()
                repository.setDialogueEnhancerAmount(profile, amount)
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting dialogue enhancer amount: ${e.message}")
            }
        }
    }

    fun resetAllProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.resetAllProfiles()
                loadSettings()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error resetting profiles: ${e.message}")
            }
        }
    }

    fun updateSpeakerState() {
        if (!isCleared) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateSpeakerState()
            }
        }
    }
    
    override fun onCleared() {
        DolbyConstants.dlog(TAG, "ViewModel onCleared")
        isCleared = true
        viewModelScope.coroutineContext.cancelChildren()
        speakerStateJob?.cancel()
        speakerStateJob = null
        profileChangeJob?.cancel()
        profileChangeJob = null
        repository.close()
        super.onCleared()
    }
    
    companion object {
        private const val TAG = "DolbyViewModel"
    }
}
