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
import android.os.Looper
import android.util.Log
import org.lunaris.dolby.data.DeviceStateManager
import org.lunaris.dolby.data.DolbyRepository

class DolbyEffectService : Service() {

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    // Use main looper so callbacks are always on a live thread regardless of
    // activity lifecycle — fixes the "no change when app is in recents" bug
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var repository: DolbyRepository
    private lateinit var deviceStateManager: DeviceStateManager

    // Tracks the device whose state is currently applied so we can snapshot
    // it before switching to a new one
    private var activeDevice: AudioDeviceInfo? = null

    // Pending device-change runnable — cancelled and re-posted on rapid
    // connect/disconnect events to avoid acting on transient states
    private var pendingDeviceChange: Runnable? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Devices added: ${addedDevices.map { it.productName }}")
            scheduleDeviceChange()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Log.d(TAG, "Devices removed: ${removedDevices.map { it.productName }}")
            // Snapshot immediately on remove — don't wait for the delay
            // because the device is already gone by the time we run
            removedDevices.forEach { device ->
                val key = deviceStateManager.deviceKey(device)
                Log.d(TAG, "Snapshotting removed device: $key")
                deviceStateManager.saveSnapshot(key, repository)
            }
            scheduleDeviceChange()
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            val isActive = configs?.any { it.isActive } == true
            if (isActive) repository.applySavedState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = DolbyRepository(this)
        deviceStateManager = DeviceStateManager(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-register callbacks here (not just onCreate) so they survive the
        // service being restarted by START_STICKY or re-started from recents
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)

        // Restore state for whatever device is currently active
        val currentDevice = getCurrentOutputDevice()
        if (currentDevice != null && activeDevice?.let {
                deviceStateManager.deviceKey(it)
            } != deviceStateManager.deviceKey(currentDevice)) {
            // Active device changed since last start (e.g. after reboot)
            activeDevice = currentDevice
            val key = deviceStateManager.deviceKey(currentDevice)
            val restored = deviceStateManager.restoreSnapshot(key, repository)
            if (!restored) repository.applySavedState()
        } else if (activeDevice == null) {
            activeDevice = currentDevice
            val key = currentDevice?.let { deviceStateManager.deviceKey(it) }
            val restored = key?.let { deviceStateManager.restoreSnapshot(it, repository) } ?: false
            if (!restored) repository.applySavedState()
        }

        Log.d(TAG, "Service started, activeDevice=${activeDevice?.productName}")
        return START_STICKY
    }

    /**
     * Schedule a device-change check after [DEVICE_SWITCH_DELAY_MS].
     *
     * The delay gives Android time to fully route audio to the new device
     * before we call getCurrentOutputDevice(). Without this, the callback
     * fires before routing completes and we still see the old device as
     * active — causing the new device to incorrectly receive the speaker's
     * settings instead of its own.
     *
     * Any pending check is cancelled first so rapid connect/disconnect
     * events don't stack up.
     */
    private fun scheduleDeviceChange() {
        pendingDeviceChange?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { handleDeviceChange() }
        pendingDeviceChange = runnable
        handler.postDelayed(runnable, DEVICE_SWITCH_DELAY_MS)
    }

    private fun handleDeviceChange() {
        pendingDeviceChange = null
        val newDevice = getCurrentOutputDevice()
        val oldDevice = activeDevice

        val newKey = newDevice?.let { deviceStateManager.deviceKey(it) }
        val oldKey = oldDevice?.let { deviceStateManager.deviceKey(it) }

        // Nothing actually changed — can happen if the removed device wasn't
        // the active one (e.g. a secondary BT device disconnected)
        if (newKey == oldKey) {
            Log.d(TAG, "Active device unchanged ($oldKey), skipping")
            return
        }

        // Snapshot the device we're leaving
        if (oldDevice != null && oldKey != null) {
            Log.d(TAG, "Snapshotting active device before switch: $oldKey")
            deviceStateManager.saveSnapshot(oldKey, repository)
        }

        // Restore the new device's state
        activeDevice = newDevice
        if (newDevice != null && newKey != null) {
            Log.d(TAG, "Switching to device: $newKey (${newDevice.productName})")
            val restored = deviceStateManager.restoreSnapshot(newKey, repository)
            if (!restored) {
                Log.d(TAG, "First time device $newKey — applying saved state as base")
                repository.applySavedState()
            }
        } else {
            Log.d(TAG, "No output device found — applying saved state")
            repository.applySavedState()
        }
    }

    /**
     * Returns the highest-priority currently connected output device.
     * Priority: BT A2DP/BLE > wired > USB > built-in speaker.
     */
    private fun getCurrentOutputDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
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

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any pending switch before we die
        pendingDeviceChange?.let { handler.removeCallbacks(it) }
        pendingDeviceChange = null
        // Snapshot current device state so it survives the restart
        activeDevice?.let { device ->
            val key = deviceStateManager.deviceKey(device)
            Log.d(TAG, "Service dying — snapshotting $key")
            deviceStateManager.saveSnapshot(key, repository)
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DolbyEffectService"

        // Delay before acting on a device change event, giving Android time
        // to complete audio routing before we query the active device.
        // 500ms covers even slow BT connection sequences reliably.
        private const val DEVICE_SWITCH_DELAY_MS = 500L

        fun start(context: Context) {
            context.startService(Intent(context, DolbyEffectService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DolbyEffectService::class.java))
        }
    }
}
