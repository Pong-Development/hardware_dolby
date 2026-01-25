/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.service.AppProfileMonitorService
import org.lunaris.dolby.service.DolbyNotificationListener

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                try {
                    val repository = DolbyRepository(context)
                    val enabled = repository.getDolbyEnabled()

                    // Main prefs (общие)
                    val mainPrefs = context.getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)

                    // Persisted vendor profile (source of truth)
                    val vendorProfile = SystemProperties
                        .get("persist.vendor.dolby.profile", "")
                        .toIntOrNull()

                    Log.d(TAG, "Boot restore - enabled: $enabled, vendorProfile: $vendorProfile")

                    if (enabled) {
                        repository.setDolbyEnabled(true)

                        if (vendorProfile != null) {
                            repository.setCurrentProfile(vendorProfile)
                            restoreProfileSettings(repository, context, vendorProfile)

                            // (опционально) синхронизируем UI-предпочтение профиля
                            mainPrefs.edit()
                                .putString(DolbyConstants.PREF_PROFILE, vendorProfile.toString())
                                .apply()
                        } else {
                            Log.d(TAG, "No persisted vendor profile, leaving HAL default")
                        }
                    }

                    if (mainPrefs.getBoolean("app_profile_monitoring_enabled", false)) {
                        AppProfileMonitorService.startMonitoring(context)
                    }

                    if (isNotificationListenerEnabled(context)) {
                        requestNotificationListenerRebind(context)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize Dolby", e)
                }
            }
        }
    }

    private fun restoreProfileSettings(repository: DolbyRepository, context: Context, profile: Int) {
        try {
            val prefs = context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)

            val ieqPreset = prefs.getString(DolbyConstants.PREF_IEQ, "0")?.toIntOrNull() ?: 0
            repository.setIeqPreset(profile, ieqPreset)

            val hpVirtualizer = prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, false)
            repository.setHeadphoneVirtualizerEnabled(profile, hpVirtualizer)

            val spkVirtualizer = prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, false)
            repository.setSpeakerVirtualizerEnabled(profile, spkVirtualizer)

            val stereoWidening = prefs.getInt(DolbyConstants.PREF_STEREO_WIDENING, 32)
            repository.setStereoWideningAmount(profile, stereoWidening)

            val dialogueEnabled = prefs.getBoolean(DolbyConstants.PREF_DIALOGUE, false)
            repository.setDialogueEnhancerEnabled(profile, dialogueEnabled)

            val dialogueAmount = prefs.getInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, 6)
            repository.setDialogueEnhancerAmount(profile, dialogueAmount)

            val bassLevel = prefs.getInt(DolbyConstants.PREF_BASS_LEVEL, 0)
            if (bassLevel > 0) repository.setBassLevel(profile, bassLevel)

            val bassCurve = prefs.getInt(DolbyConstants.PREF_BASS_CURVE, 0)
            repository.setBassCurve(profile, bassCurve)

            val trebleLevel = prefs.getInt(DolbyConstants.PREF_TREBLE_LEVEL, 0)
            if (trebleLevel > 0) repository.setTrebleLevel(profile, trebleLevel)

            val volumeLeveler = prefs.getBoolean(DolbyConstants.PREF_VOLUME, false)
            repository.setVolumeLevelerEnabled(profile, volumeLeveler)

            Log.d(TAG, "Successfully restored all settings for profile $profile")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore profile settings", e)
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, DolbyNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    private fun requestNotificationListenerRebind(context: Context) {
        try {
            val cn = ComponentName(context, DolbyNotificationListener::class.java)
            DolbyNotificationListener::class.java.getMethod(
                "requestRebind",
                ComponentName::class.java
            ).invoke(null, cn)
            Log.d(TAG, "Requested notification listener rebind")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request notification listener rebind", e)
        }
    }

    companion object {
        private const val TAG = "Dolby-Boot"
    }
}
