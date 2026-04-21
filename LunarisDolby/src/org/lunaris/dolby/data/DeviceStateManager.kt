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

/**
 * Manages per-device Dolby state snapshots.
 *
 * Every time a device disconnects, a full snapshot of the current Dolby state
 * is saved under a key derived from the device's type + name (or address for BT).
 * When a device connects, its last snapshot is restored exactly as it was left —
 * unsaved EQ tweaks included.
 *
 * Versioning: [SNAPSHOT_VERSION] is stored with every snapshot. On restore, if
 * the stored version doesn't match the current version the snapshot is discarded
 * and defaults are applied rather than silently restoring corrupt or incompatible
 * state. Bump [SNAPSHOT_VERSION] whenever the schema changes (new keys, changed
 * ranges, removed params, EQ band count changes, etc).
 *
 * Storage: one SharedPreferences file per device key ("device_state_<key>").
 */
class DeviceStateManager(private val context: Context) {

    // ─── Device identification ────────────────────────────────────────────────

    /** Returns a stable string key for a given AudioDeviceInfo. */
    fun deviceKey(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                // Use MAC address so each physical BT device has its own slot
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

    /** Human-readable display name for a device, shown in UI. */
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

    // ─── Snapshot ─────────────────────────────────────────────────────────────

    /** Snapshot the full current Dolby state for the given device. */
    fun saveSnapshot(deviceKey: String, repository: DolbyRepository) {
        val profile = repository.getCurrentProfile()
        val prefs = getDevicePrefs(deviceKey)
        val editor = prefs.edit()

        // Always write version first so restore can gate on it
        editor.putInt(KEY_VERSION, SNAPSHOT_VERSION)

        editor.putBoolean(KEY_DOLBY_ENABLED, repository.getDolbyEnabled())
        editor.putInt(KEY_PROFILE, profile)

        // IEQ
        editor.putInt(KEY_IEQ, repository.getIeqPreset(profile))

        // Virtualizers
        editor.putBoolean(KEY_HP_VIRT, repository.getHeadphoneVirtualizerEnabled(profile))
        editor.putBoolean(KEY_SPK_VIRT, repository.getSpeakerVirtualizerEnabled(profile))

        // Dialogue enhancer
        editor.putBoolean(KEY_DIALOGUE, repository.getDialogueEnhancerEnabled(profile))
        editor.putInt(KEY_DIALOGUE_AMT, repository.getDialogueEnhancerAmount(profile))

        // Bass enhancer — enabled flag + level + curve all saved explicitly
        editor.putBoolean(KEY_BASS_ENABLED, repository.getBassEnhancerEnabled(profile))
        editor.putInt(KEY_BASS_LEVEL, repository.getBassLevel(profile))
        editor.putInt(KEY_BASS_CURVE, repository.getBassCurve(profile))

        // Treble enhancer — enabled flag + level saved explicitly
        editor.putBoolean(KEY_TREBLE_ENABLED, repository.getTrebleEnhancerEnabled(profile))
        editor.putInt(KEY_TREBLE_LEVEL, repository.getTrebleLevel(profile))

        // Mid enhancer — enabled flag + level saved explicitly
        editor.putBoolean(KEY_MID_ENABLED, repository.getMidEnhancerEnabled(profile))
        editor.putInt(KEY_MID_LEVEL, repository.getMidLevel(profile))

        // Optional params — only saved if device supports them
        if (repository.volumeLevelerSupported) {
            editor.putBoolean(KEY_VOLUME, repository.getVolumeLevelerEnabled(profile))
        }
        if (repository.stereoWideningSupported) {
            editor.putInt(KEY_STEREO, repository.getStereoWideningAmount(profile))
        }

        // EQ band gains — store band count alongside gains so restore can
        // validate the count matches before applying (guards against band
        // mode changes across versions)
        val gains = repository.getEqualizerGains(profile, BandMode.TWENTY_BAND)
        editor.putInt(KEY_EQ_BAND_COUNT, gains.size)
        editor.putString(KEY_EQ_GAINS, gains.joinToString(",") { it.gain.toString() })

        editor.apply()
        DolbyConstants.dlog(TAG,
            "Snapshot saved for device=$deviceKey profile=$profile bands=${gains.size} v=$SNAPSHOT_VERSION")
    }

    // ─── Restore ──────────────────────────────────────────────────────────────

    /**
     * Restore a previously saved snapshot for the given device.
     *
     * Returns true if a valid, version-matching snapshot was found and restored.
     * Returns false if:
     *   - No snapshot exists (first-time device)
     *   - Snapshot version doesn't match [SNAPSHOT_VERSION] (stale/incompatible)
     *   - Any exception occurs during restore
     *
     * In all false cases the caller should apply defaults instead.
     */
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

            // EQ gains — validate band count before applying to avoid
            // partially-restored state if the count changed across versions
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

            // IEQ
            repository.setIeqPreset(profile, prefs.getInt(KEY_IEQ, 0))

            // Virtualizers
            repository.setHeadphoneVirtualizerEnabled(profile, prefs.getBoolean(KEY_HP_VIRT, false))
            repository.setSpeakerVirtualizerEnabled(profile, prefs.getBoolean(KEY_SPK_VIRT, false))

            // Dialogue enhancer
            repository.setDialogueEnhancerEnabled(profile, prefs.getBoolean(KEY_DIALOGUE, false))
            repository.setDialogueEnhancerAmount(profile, prefs.getInt(KEY_DIALOGUE_AMT, 6))

            // Bass enhancer — restore enabled state explicitly, then level and curve
            repository.setBassEnhancerEnabled(profile, prefs.getBoolean(KEY_BASS_ENABLED, false))
            repository.setBassCurve(profile, prefs.getInt(KEY_BASS_CURVE, 0))
            repository.setBassLevel(profile, prefs.getInt(KEY_BASS_LEVEL, 0))

            // Treble enhancer — restore enabled state explicitly, then level
            repository.setTrebleEnhancerEnabled(profile, prefs.getBoolean(KEY_TREBLE_ENABLED, false))
            repository.setTrebleLevel(profile, prefs.getInt(KEY_TREBLE_LEVEL, 0))

            // Mid enhancer — restore enabled state explicitly, then level
            repository.setMidEnhancerEnabled(profile, prefs.getBoolean(KEY_MID_ENABLED, false))
            repository.setMidLevel(profile, prefs.getInt(KEY_MID_LEVEL, 0))

            // Optional params
            if (repository.volumeLevelerSupported) {
                repository.setVolumeLevelerEnabled(profile, prefs.getBoolean(KEY_VOLUME, false))
            }
            if (repository.stereoWideningSupported) {
                repository.setStereoWideningAmount(profile, prefs.getInt(KEY_STEREO, 32))
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

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Returns true if a valid version-matching snapshot exists for this device. */
    fun hasSnapshot(deviceKey: String): Boolean {
        val prefs = getDevicePrefs(deviceKey)
        return prefs.contains(KEY_VERSION) &&
                prefs.getInt(KEY_VERSION, -1) == SNAPSHOT_VERSION
    }

    /** Clears the snapshot for a specific device. */
    fun clearSnapshot(deviceKey: String) {
        getDevicePrefs(deviceKey).edit().clear().apply()
        DolbyConstants.dlog(TAG, "Snapshot cleared for device=$deviceKey")
    }

    /** Returns list of all device keys that have stored snapshots. */
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

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "DeviceStateManager"

        /**
         * Bump this whenever the snapshot schema changes:
         * - New parameters added or removed
         * - EQ band count or frequency list changes
         * - Parameter value ranges change
         * - Feature flags added that affect restore behaviour
         *
         * Old snapshots with a different version are discarded on restore
         * rather than silently applying potentially corrupt state.
         */
        const val SNAPSHOT_VERSION = 1

        private const val KEY_VERSION       = "snapshot_version"
        private const val KEY_DOLBY_ENABLED = "enabled"
        private const val KEY_PROFILE       = "profile"
        private const val KEY_IEQ           = "ieq"
        private const val KEY_HP_VIRT       = "hp_virt"
        private const val KEY_SPK_VIRT      = "spk_virt"
        private const val KEY_DIALOGUE      = "dialogue"
        private const val KEY_DIALOGUE_AMT  = "dialogue_amt"
        private const val KEY_BASS_ENABLED  = "bass_enabled"
        private const val KEY_BASS_LEVEL    = "bass_level"
        private const val KEY_BASS_CURVE    = "bass_curve"
        private const val KEY_TREBLE_ENABLED = "treble_enabled"
        private const val KEY_TREBLE_LEVEL  = "treble_level"
        private const val KEY_MID_ENABLED   = "mid_enabled"
        private const val KEY_MID_LEVEL     = "mid_level"
        private const val KEY_VOLUME        = "volume"
        private const val KEY_STEREO        = "stereo"
        private const val KEY_EQ_BAND_COUNT = "eq_band_count"
        private const val KEY_EQ_GAINS      = "eq_gains"
    }
}
