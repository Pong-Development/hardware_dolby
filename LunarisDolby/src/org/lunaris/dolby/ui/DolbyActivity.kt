/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.ui.screens.DolbyNavHost
import org.lunaris.dolby.ui.theme.DolbyTheme
import org.lunaris.dolby.ui.viewmodel.DolbyViewModel
import org.lunaris.dolby.ui.viewmodel.EqualizerViewModel

class DolbyActivity : ComponentActivity() {

    private val dolbyViewModel: DolbyViewModel by viewModels()
    private val equalizerViewModel: EqualizerViewModel by viewModels()
    
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    
    private var isAudioCallbackRegistered = false
    private var isActivityActive = false
    
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if (isActivityActive) {
                DolbyConstants.dlog(TAG, "Audio device added")
                handler.post {
                    dolbyViewModel.updateSpeakerState()
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            if (isActivityActive) {
                DolbyConstants.dlog(TAG, "Audio device removed")
                handler.post {
                    dolbyViewModel.updateSpeakerState()
                }
            }
        }
    }
    
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        
        override fun onCreate(owner: LifecycleOwner) {
            super.onCreate(owner)
            DolbyConstants.dlog(TAG, "Lifecycle: onCreate")
        }
        
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            DolbyConstants.dlog(TAG, "Lifecycle: onStart")
            isActivityActive = true
            registerAudioCallback()
            dolbyViewModel.loadSettings()
        }
        
        override fun onResume(owner: LifecycleOwner) {
            super.onResume(owner)
            DolbyConstants.dlog(TAG, "Lifecycle: onResume")
            dolbyViewModel.updateSpeakerState()
        }
        
        override fun onPause(owner: LifecycleOwner) {
            super.onPause(owner)
            DolbyConstants.dlog(TAG, "Lifecycle: onPause")
        }
        
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            DolbyConstants.dlog(TAG, "Lifecycle: onStop")
            isActivityActive = false
            if (!isChangingConfigurations) {
                unregisterAudioCallback()
            }
        }
        
        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            DolbyConstants.dlog(TAG, "Lifecycle: onDestroy")
            cleanupResources()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DolbyConstants.dlog(TAG, "Activity onCreate")
        lifecycle.addObserver(lifecycleObserver)
        
        setContent {
            DolbyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    // Matches the Scaffold container the screens paint, so no
                    // seam shows behind system bars or during transitions.
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    DolbyNavHost(
                        dolbyViewModel = dolbyViewModel,
                        equalizerViewModel = equalizerViewModel
                    )
                }
            }
        }
    }
    
    private fun registerAudioCallback() {
        if (!isAudioCallbackRegistered) {
            try {
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
                isAudioCallbackRegistered = true
                DolbyConstants.dlog(TAG, "Audio callback registered")
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to register audio callback: ${e.message}")
            }
        }
    }
    
    private fun unregisterAudioCallback() {
        if (isAudioCallbackRegistered) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
                isAudioCallbackRegistered = false
                DolbyConstants.dlog(TAG, "Audio callback unregistered")
            } catch (e: Exception) {
                DolbyConstants.dlog(TAG, "Failed to unregister audio callback: ${e.message}")
            }
        }
    }
    
    private fun cleanupResources() {
        try {
            unregisterAudioCallback()
            handler.removeCallbacksAndMessages(null)
            lifecycle.removeObserver(lifecycleObserver)
            DolbyConstants.dlog(TAG, "Resources cleaned up successfully")
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Error during cleanup: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        DolbyConstants.dlog(TAG, "Activity onDestroy")
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "DolbyActivity"
    }
}
