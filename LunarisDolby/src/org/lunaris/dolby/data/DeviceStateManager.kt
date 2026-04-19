/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.DolbyConstants.DsParam

/**
 * Manages per-device Dolby state snapshots.
 *
 * Every time a device disconnects, a full snapshot of the current Dolby state
 * is saved under a key derived from the device's type + name (or address for BT).
 * When a device connects, its last snapshot is restored exactly as it was left —
 * unsaved EQ tweaks included.
 *
 * Storage: one SharedPreferences file per device key ("device_state_<key>").
 * Each file mirrors the same keys used by DolbyRepository's profile_N prefs,
 * plus the active profile itself.
 */
class DeviceStateManager(private val context: Context) {

    /** Returns a stable string key for a given AudioDeviceInfo. */
    fun deviceKey(device: AudioDeviceInfo): String {
        val type = device.type
        // For BT devices use address so each physical device has its own state.
        // For wired/builtin use type only (they're interchangeable).
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                val addr = device.address?.takeIf { it.isNotBlank() } ?: "unknown"
                "bt_${addr.replace(":", "_")}"
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> "wired_headphones"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin_speaker"
            else -> "device_type_$type"
        }
    }

    /** Human-readable display name for a device key, shown in UI. */
    fun deviceDisplayName(device: AudioDeviceInfo): String {
        val productName = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> productName ?: "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE -> productName ?: "USB Audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> productName ?: "Bluetooth Device"
            else -> productName ?: "Audio Device"
        }
    }

    /** Snapshot the full current Dolby state for the given device. */
    fun saveSnapshot(deviceKey: String, repository: DolbyRepository) {
        val profile = repository.getCurrentProfile()
        val prefs = getDevicePrefs(deviceKey)
        val editor = prefs.edit()

        editor.putBoolean(KEY_DOLBY_ENABLED, repository.getDolbyEnabled())
        editor.putInt(KEY_PROFILE, profile)

        // Per-profile DAP params
        editor.putInt(KEY_IEQ, repository.getIeqPreset(profile))
        editor.putBoolean(KEY_HP_VIRT, repository.getHeadphoneVirtualizerEnabled(profile))
        editor.putBoolean(KEY_SPK_VIRT, repository.getSpeakerVirtualizerEnabled(profile))
        editor.putBoolean(KEY_DIALOGUE, repository.getDialogueEnhancerEnabled(profile))
        editor.putInt(KEY_DIALOGUE_AMT, repository.getDialogueEnhancerAmount(profile))
        editor.putBoolean(KEY_BASS, repository.getBassEnhancerEnabled(profile))
        editor.putInt(KEY_BASS_LEVEL, repository.getBassLevel(profile))
        editor.putInt(KEY_BASS_CURVE, repository.getBassCurve(profile))
        editor.putBoolean(KEY_TREBLE, repository.getTrebleEnhancerEnabled(profile))
        editor.putInt(KEY_TREBLE_LEVEL, repository.getTrebleLevel(profile))
        editor.putBoolean(KEY_MID, repository.getMidEnhancerEnabled(profile))
        editor.putInt(KEY_MID_LEVEL, repository.getMidLevel(profile))

        if (repository.volumeLevelerSupported) {
            editor.putBoolean(KEY_VOLUME, repository.getVolumeLevelerEnabled(profile))
        }
        if (repository.stereoWideningSupported) {
            editor.putInt(KEY_STEREO, repository.getStereoWideningAmount(profile))
        }

        // EQ band gains (all 20 bands as comma-separated string)
        val gains = repository.getEqualizerGains(profile,
            org.lunaris.dolby.domain.models.BandMode.TWENTY_BAND)
        val gainsStr = gains.joinToString(",") { it.gain.toString() }
        editor.putString(KEY_EQ_GAINS, gainsStr)

        editor.apply()
        DolbyConstants.dlog(TAG, "Snapshot saved for device=$deviceKey profile=$profile")
    }

    /**
     * Restore a previously saved snapshot for the given device.
     * Returns true if a snapshot existed, false if this is a first-time device
     * (in which case the caller should apply defaults).
     */
    fun restoreSnapshot(deviceKey: String, repository: DolbyRepository): Boolean {
        val prefs = getDevicePrefs(deviceKey)
        if (!prefs.contains(KEY_PROFILE)) {
            DolbyConstants.dlog(TAG, "No snapshot for device=$deviceKey, using defaults")
            return false
        }

        try {
            val enabled = prefs.getBoolean(KEY_DOLBY_ENABLED, true)
            val profile = prefs.getInt(KEY_PROFILE, 0)

            repository.setDolbyEnabled(enabled)
            repository.setCurrentProfile(profile)

            // Restore EQ gains first (they're the base for other enhancers)
            val gainsStr = prefs.getString(KEY_EQ_GAINS, null)
            if (gainsStr != null) {
                val gains = gainsStr.split(",").mapNotNull { it.toIntOrNull() }
                if (gains.size == 20) {
                    val bandGains = gains.mapIndexed { i, g ->
                        org.lunaris.dolby.domain.models.BandGain(
                            frequency = org.lunaris.dolby.data.DolbyRepository.BAND_FREQUENCIES_20
                                .getOrElse(i) { i },
                            gain = g
                        )
                    }
                    repository.setEqualizerGains(profile, bandGains,
                        org.lunaris.dolby.domain.models.BandMode.TWENTY_BAND)
                }
            }

            // Restore all other params
            repository.setIeqPreset(profile, prefs.getInt(KEY_IEQ, 0))
            repository.setHeadphoneVirtualizerEnabled(profile,
                prefs.getBoolean(KEY_HP_VIRT, false))
            repository.setSpeakerVirtualizerEnabled(profile,
                prefs.getBoolean(KEY_SPK_VIRT, false))
            repository.setDialogueEnhancerEnabled(profile,
                prefs.getBoolean(KEY_DIALOGUE, false))
            repository.setDialogueEnhancerAmount(profile,
                prefs.getInt(KEY_DIALOGUE_AMT, 6))

            val bassLevel = prefs.getInt(KEY_BASS_LEVEL, 0)
            val bassCurve = prefs.getInt(KEY_BASS_CURVE, 0)
            repository.setBassCurve(profile, bassCurve)
            repository.setBassLevel(profile, bassLevel)

            val trebleLevel = prefs.getInt(KEY_TREBLE_LEVEL, 0)
            repository.setTrebleLevel(profile, trebleLevel)

            val midLevel = prefs.getInt(KEY_MID_LEVEL, 0)
            repository.setMidLevel(profile, midLevel)

            if (repository.volumeLevelerSupported) {
                repository.setVolumeLevelerEnabled(profile,
                    prefs.getBoolean(KEY_VOLUME, false))
            }
            if (repository.stereoWideningSupported) {
                repository.setStereoWideningAmount(profile,
                    prefs.getInt(KEY_STEREO, 32))
            }

            DolbyConstants.dlog(TAG, "Snapshot restored for device=$deviceKey profile=$profile")
            return true
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG, "Failed to restore snapshot for $deviceKey: ${e.message}")
            return false
        }
    }

    /** Returns true if a snapshot exists for this device key. */
    fun hasSnapshot(deviceKey: String): Boolean {
        return getDevicePrefs(deviceKey).contains(KEY_PROFILE)
    }

    /** Returns list of all device keys that have stored snapshots. */
    fun getAllDeviceKeys(): List<String> {
        // SharedPreferences files are stored as <name>.xml in the app's shared_prefs dir
        val dir = context.filesDir.parentFile?.let {
            java.io.File(it, "shared_prefs")
        } ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.name.startsWith("device_state_") }
            ?.map { it.name.removePrefix("device_state_").removeSuffix(".xml") }
            ?: emptyList()
    }

    private fun getDevicePrefs(deviceKey: String): SharedPreferences {
        return context.getSharedPreferences("device_state_$deviceKey", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "DeviceStateManager"
        private const val KEY_DOLBY_ENABLED = "enabled"
        private const val KEY_PROFILE = "profile"
        private const val KEY_IEQ = "ieq"
        private const val KEY_HP_VIRT = "hp_virt"
        private const val KEY_SPK_VIRT = "spk_virt"
        private const val KEY_DIALOGUE = "dialogue"
        private const val KEY_DIALOGUE_AMT = "dialogue_amt"
        private const val KEY_BASS = "bass"
        private const val KEY_BASS_LEVEL = "bass_level"
        private const val KEY_BASS_CURVE = "bass_curve"
        private const val KEY_TREBLE = "treble"
        private const val KEY_TREBLE_LEVEL = "treble_level"
        private const val KEY_MID = "mid"
        private const val KEY_MID_LEVEL = "mid_level"
        private const val KEY_VOLUME = "volume"
        private const val KEY_STEREO = "stereo"
        private const val KEY_EQ_GAINS = "eq_gains"
    }
}
