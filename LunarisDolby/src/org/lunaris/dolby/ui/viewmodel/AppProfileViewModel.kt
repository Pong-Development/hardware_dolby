/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.AppProfileManager
import org.lunaris.dolby.domain.models.AppProfileUiState
import org.lunaris.dolby.utils.ToastHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren

class AppProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val appProfileManager = AppProfileManager(application)
    private val context = application

    private val _uiState = MutableStateFlow<AppProfileUiState>(AppProfileUiState.Loading)
    val uiState: StateFlow<AppProfileUiState> = _uiState.asStateFlow()
    
    @Volatile private var isCleared = false

    init {
        DolbyConstants.dlog(TAG, "ViewModel initialized")
        loadApps()
    }

    fun loadApps() {
        if (isCleared) {
            DolbyConstants.dlog(TAG, "ViewModel cleared, skipping loadApps")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = AppProfileUiState.Loading
                val apps = appProfileManager.getInstalledApps()
                val appsWithProfiles = appProfileManager.getAppsWithProfiles()
                
                if (!isCleared) {
                    _uiState.value = AppProfileUiState.Success(
                        apps = apps,
                        appsWithProfiles = appsWithProfiles
                    )
                }
            } catch (e: Exception) {
                if (!isCleared) {
                    DolbyConstants.dlog(TAG, "Error loading apps: ${e.message}")
                    _uiState.value = AppProfileUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun setAppProfile(packageName: String, profile: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (profile == -1) {
                    appProfileManager.removeAppProfile(packageName)
                    ToastHelper.showToast(context, "Profile reset to default")
                } else {
                    appProfileManager.setAppProfile(packageName, profile)
                    val profileName = getProfileName(profile)
                    ToastHelper.showToast(context, "Profile set to: $profileName")
                }
                loadApps()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error setting app profile: ${e.message}")
            }
        }
    }

    fun removeAppProfile(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appProfileManager.removeAppProfile(packageName)
                ToastHelper.showToast(context, "Profile removed")
                loadApps()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error removing app profile: ${e.message}")
            }
        }
    }

    fun clearAllAppProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appProfileManager.clearAllAppProfiles()
                ToastHelper.showToast(context, "All app profiles cleared")
                loadApps()
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Error clearing app profiles: ${e.message}")
            }
        }
    }
    
    private fun getProfileName(profile: Int): String {
        return try {
            val profiles = context.resources.getStringArray(R.array.dolby_profile_entries)
            val profileValues = context.resources.getStringArray(R.array.dolby_profile_values)
            
            val index = profileValues.indexOfFirst { it.toInt() == profile }
            if (index >= 0) profiles[index] else "Unknown"
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error getting profile name: ${e.message}")
            "Unknown"
        }
    }
    
    override fun onCleared() {
        DolbyConstants.dlog(TAG, "ViewModel onCleared")
        isCleared = true
        viewModelScope.coroutineContext.cancelChildren()
        super.onCleared()
    }
    
    companion object {
        private const val TAG = "AppProfileViewModel"
    }
}
