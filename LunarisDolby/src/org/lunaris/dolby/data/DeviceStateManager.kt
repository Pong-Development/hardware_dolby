/*
 * Copyright (C) 2026 tranQuila-Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceInfo

import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.domain.models.BandGain
import org.lunaris.dolby.domain.models.BandMode

class DeviceStateManager(private val context: Context) {

    fun deviceKey(device: AudioDeviceInfo): String {
        return when (device.type) {
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
            else -> "device_type_${device.type}"
        }
    }

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

    fun saveSnapshot(deviceKey: String, repository: DolbyRepository) {
        val profile = repository.getCurrentProfile()
        val prefs = getDevicePrefs(deviceKey)
        val editor = prefs.edit()

        editor.putInt(KEY_VERSION, SNAPSHOT_VERSION)

        editor.putBoolean(KEY_DOLBY_ENABLED, repository.getDolbyEnabled())
        editor.putInt(KEY_PROFILE, profile)

        editor.putInt(KEY_IEQ, repository.getIeqPreset(profile))

        editor.putBoolean(KEY_HP_VIRT, repository.getHeadphoneVirtualizerEnabled(profile))
        editor.putBoolean(KEY_SPK_VIRT, repository.getSpeakerVirtualizerEnabled(profile))

        editor.putBoolean(KEY_DIALOGUE, repository.getDialogueEnhancerEnabled(profile))
        editor.putInt(KEY_DIALOGUE_AMT, repository.getDialogueEnhancerAmount(profile))

        editor.putBoolean(KEY_BASS_ENABLED, repository.getBassEnhancerEnabled(profile))
        editor.putInt(KEY_BASS_LEVEL, repository.getBassLevel(profile))
        editor.putInt(KEY_BASS_CURVE, repository.getBassCurve(profile))

        editor.putBoolean(KEY_TREBLE_ENABLED, repository.getTrebleEnhancerEnabled(profile))
        editor.putInt(KEY_TREBLE_LEVEL, repository.getTrebleLevel(profile))

        editor.putBoolean(KEY_MID_ENABLED, repository.getMidEnhancerEnabled(profile))
        editor.putInt(KEY_MID_LEVEL, repository.getMidLevel(profile))

        if (repository.volumeLevelerSupported) {
            editor.putBoolean(KEY_VOLUME, repository.getVolumeLevelerEnabled(profile))
        }
        if (repository.stereoWideningSupported) {
            editor.putInt(KEY_STEREO, repository.getStereoWideningAmount(profile))
        }

        val gains = repository.getEqualizerGains(profile, BandMode.TWENTY_BAND)
        editor.putInt(KEY_EQ_BAND_COUNT, gains.size)
        editor.putString(KEY_EQ_GAINS, gains.joinToString(",") { it.gain.toString() })

        editor.apply()
        DolbyConstants.dlog(TAG,
            "Snapshot saved for device=$deviceKey profile=$profile bands=${gains.size} v=$SNAPSHOT_VERSION")
    }

    fun restoreSnapshot(deviceKey: String, repository: DolbyRepository): Boolean {
        val prefs = getDevicePrefs(deviceKey)

        if (!prefs.contains(KEY_VERSION)) {
            DolbyConstants.dlog(TAG, "No snapshot for device=$deviceKey")
            return false
        }

        val storedVersion = prefs.getInt(KEY_VERSION, -1)
        if (storedVersion != SNAPSHOT_VERSION) {
            DolbyConstants.dlog(TAG,
                "Snapshot version mismatch for $deviceKey: stored=$storedVersion current=$SNAPSHOT_VERSION — discarding")
            clearSnapshot(deviceKey)
            return false
        }

        return try {
            val enabled = prefs.getBoolean(KEY_DOLBY_ENABLED, true)
            val profile = prefs.getInt(KEY_PROFILE, 0)

            repository.setDolbyEnabled(enabled)
            repository.setCurrentProfile(profile)

            repository.setIeqPreset(profile, prefs.getInt(KEY_IEQ, 0))

            repository.setHeadphoneVirtualizerEnabled(profile, prefs.getBoolean(KEY_HP_VIRT, false))
            repository.setSpeakerVirtualizerEnabled(profile, prefs.getBoolean(KEY_SPK_VIRT, false))

            repository.setDialogueEnhancerEnabled(profile, prefs.getBoolean(KEY_DIALOGUE, false))
            repository.setDialogueEnhancerAmount(profile, prefs.getInt(KEY_DIALOGUE_AMT, 6))

            repository.setBassEnhancerEnabled(profile, prefs.getBoolean(KEY_BASS_ENABLED, false))
            repository.setBassCurve(profile, prefs.getInt(KEY_BASS_CURVE, 0))
            repository.setBassLevel(profile, prefs.getInt(KEY_BASS_LEVEL, 0))

            repository.setTrebleEnhancerEnabled(profile, prefs.getBoolean(KEY_TREBLE_ENABLED, false))
            repository.setTrebleLevel(profile, prefs.getInt(KEY_TREBLE_LEVEL, 0))

            repository.setMidEnhancerEnabled(profile, prefs.getBoolean(KEY_MID_ENABLED, false))
            repository.setMidLevel(profile, prefs.getInt(KEY_MID_LEVEL, 0))

            if (repository.volumeLevelerSupported) {
                repository.setVolumeLevelerEnabled(profile, prefs.getBoolean(KEY_VOLUME, false))
            }
            if (repository.stereoWideningSupported) {
                repository.setStereoWideningAmount(profile, prefs.getInt(KEY_STEREO, 32))
            }

            val storedBandCount = prefs.getInt(KEY_EQ_BAND_COUNT, -1)
            val gainsStr = prefs.getString(KEY_EQ_GAINS, null)
            if (gainsStr != null && storedBandCount > 0) {
                val gains = gainsStr.split(",").mapNotNull { it.toIntOrNull() }
                if (gains.size == storedBandCount) {
                    val bandGains = gains.mapIndexed { i, g ->
                        BandGain(
                            frequency = DolbyRepository.BAND_FREQUENCIES_20.getOrElse(i) { i },
                            gain = g
                        )
                    }
                    repository.setEqualizerGains(profile, bandGains, BandMode.TWENTY_BAND)
                } else {
                    DolbyConstants.dlog(TAG,
                        "EQ band count mismatch for $deviceKey: stored=$storedBandCount actual=${gains.size} — skipping EQ restore")
                }
            }

            DolbyConstants.dlog(TAG,
                "Snapshot restored for device=$deviceKey profile=$profile v=$storedVersion")
            true
        } catch (e: Exception) {
            DolbyConstants.dlog(TAG,
                "Failed to restore snapshot for $deviceKey: ${e.message} — discarding")
            clearSnapshot(deviceKey)
            false
        }
    }

    fun hasSnapshot(deviceKey: String): Boolean {
        val prefs = getDevicePrefs(deviceKey)
        return prefs.contains(KEY_VERSION) &&
                prefs.getInt(KEY_VERSION, -1) == SNAPSHOT_VERSION
    }

    fun clearSnapshot(deviceKey: String) {
        getDevicePrefs(deviceKey).edit().clear().apply()
        DolbyConstants.dlog(TAG, "Snapshot cleared for device=$deviceKey")
    }

    fun getAllDeviceKeys(): List<String> {
        val dir = context.filesDir.parentFile?.let {
            java.io.File(it, "shared_prefs")
        } ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.name.startsWith("device_state_") }
            ?.map { it.name.removePrefix("device_state_").removeSuffix(".xml") }
            ?: emptyList()
    }

    private fun getDevicePrefs(deviceKey: String): SharedPreferences =
        context.getSharedPreferences("device_state_$deviceKey", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DeviceStateManager"

        const val SNAPSHOT_VERSION = 1

        private const val KEY_VERSION = "snapshot_version"
        private const val KEY_DOLBY_ENABLED = "enabled"
        private const val KEY_PROFILE = "profile"
        private const val KEY_IEQ = "ieq"
        private const val KEY_HP_VIRT = "hp_virt"
        private const val KEY_SPK_VIRT = "spk_virt"
        private const val KEY_DIALOGUE = "dialogue"
        private const val KEY_DIALOGUE_AMT  = "dialogue_amt"
        private const val KEY_BASS_ENABLED = "bass_enabled"
        private const val KEY_BASS_LEVEL = "bass_level"
        private const val KEY_BASS_CURVE = "bass_curve"
        private const val KEY_TREBLE_ENABLED = "treble_enabled"
        private const val KEY_TREBLE_LEVEL = "treble_level"
        private const val KEY_MID_ENABLED = "mid_enabled"
        private const val KEY_MID_LEVEL = "mid_level"
        private const val KEY_VOLUME = "volume"
        private const val KEY_STEREO = "stereo"
        private const val KEY_EQ_BAND_COUNT = "eq_band_count"
        private const val KEY_EQ_GAINS = "eq_gains"
    }
}
