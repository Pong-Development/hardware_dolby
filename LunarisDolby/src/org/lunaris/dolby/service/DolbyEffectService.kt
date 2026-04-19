/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.util.Log
import org.lunaris.dolby.data.DeviceStateManager
import org.lunaris.dolby.data.DolbyRepository

class DolbyEffectService : Service() {

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val handler = Handler()
    private lateinit var repository: DolbyRepository
    private lateinit var deviceStateManager: DeviceStateManager

    /**
     * Tracks which device was active before the latest change so we can
     * snapshot its state before switching to the new device.
     */
    private var previousActiveDevice: AudioDeviceInfo? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Devices added: ${addedDevices.map { it.productName }}")
            handleDeviceChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Devices removed: ${removedDevices.map { it.productName }}")
            // Snapshot the state of the device that was just removed
            removedDevices.forEach { device ->
                val key = deviceStateManager.deviceKey(device)
                Log.d(TAG, "Snapshotting state for removed device: $key")
                deviceStateManager.saveSnapshot(key, repository)
            }
            handleDeviceChange()
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val isActive = configs?.any { it.isActive } == true
            if (isActive) {
                repository.applySavedState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = DolbyRepository(this)
        deviceStateManager = DeviceStateManager(this)

        // Determine initial active device and restore its state
        val currentDevice = getCurrentOutputDevice()
        if (currentDevice != null) {
            previousActiveDevice = currentDevice
            val key = deviceStateManager.deviceKey(currentDevice)
            val restored = deviceStateManager.restoreSnapshot(key, repository)
            if (!restored) {
                // First time seeing this device — apply defaults
                repository.applySavedState()
            }
        } else {
            repository.applySavedState()
        }

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        Log.d(TAG, "Dolby effect service created")
    }

    private fun handleDeviceChange() {
        val newDevice = getCurrentOutputDevice()
        val oldDevice = previousActiveDevice

        // Snapshot the previous device's state before we switch
        if (oldDevice != null) {
            val oldKey = deviceStateManager.deviceKey(oldDevice)
            Log.d(TAG, "Saving snapshot for previous device: $oldKey")
            deviceStateManager.saveSnapshot(oldKey, repository)
        }

        // Restore new device's state (or apply defaults if first time)
        if (newDevice != null) {
            val newKey = deviceStateManager.deviceKey(newDevice)
            Log.d(TAG, "Restoring snapshot for new device: $newKey")
            val restored = deviceStateManager.restoreSnapshot(newKey, repository)
            if (!restored) {
                // First time connecting this device — start from current saved state
                Log.d(TAG, "First time device, applying saved state as base")
                repository.applySavedState()
            }
            previousActiveDevice = newDevice
        } else {
            repository.updateSpeakerState()
            repository.applySavedState()
            previousActiveDevice = null
        }
    }

    /**
     * Returns the currently active output device for media playback.
     * Priority: BT A2DP/BLE > wired > USB > speaker.
     */
    private fun getCurrentOutputDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Priority order: BT > wired > USB > speaker
        val priorityOrder = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )

        for (type in priorityOrder) {
            val device = devices.firstOrNull { it.type == type }
            if (device != null) return device
        }
        return devices.firstOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Snapshot state of the current device before service dies
        previousActiveDevice?.let { device ->
            val key = deviceStateManager.deviceKey(device)
            deviceStateManager.saveSnapshot(key, repository)
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Dolby effect service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DolbyEffectService"

        fun start(context: Context) {
            val intent = Intent(context, DolbyEffectService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DolbyEffectService::class.java)
            context.stopService(intent)
        }
    }
}
