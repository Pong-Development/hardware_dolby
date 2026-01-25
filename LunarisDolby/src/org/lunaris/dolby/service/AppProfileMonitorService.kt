/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.lunaris.dolby.DolbyConstants
import org.lunaris.dolby.R
import org.lunaris.dolby.data.AppProfileManager
import org.lunaris.dolby.data.DolbyRepository
import org.lunaris.dolby.utils.ToastHelper
import java.util.concurrent.atomic.AtomicReference

class AppProfileMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val switchHandler = Handler(Looper.getMainLooper())
    private lateinit var appProfileManager: AppProfileManager
    private lateinit var dolbyRepository: DolbyRepository
    private lateinit var audioManager: AudioManager
    private val lastPackageName = AtomicReference<String?>(null)
    private var originalProfile: Int = -1
    private var isMonitoring = false
    private var pendingSwitchRunnable: Runnable? = null
    private var hasOriginalProfile = false
    private var lastProfileChangeTime: Long = 0

    private val checkForegroundAppRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            if (isMonitoring) {
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appProfileManager = AppProfileManager(this)
        dolbyRepository = DolbyRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
        val savedProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
        
        if (!hasOriginalProfile) {
            originalProfile = savedProfile
            hasOriginalProfile = true
            DolbyConstants.dlog(TAG, "Service created - saved original profile: $originalProfile")
        } else {
            DolbyConstants.dlog(TAG, "Service created - keeping existing original profile: $originalProfile")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            
            if (!hasOriginalProfile) {
                val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                originalProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
                hasOriginalProfile = true
                DolbyConstants.dlog(TAG, "Re-initialized original profile on start: $originalProfile")
            }
            
            DolbyConstants.dlog(TAG, "Started monitoring foreground app (original profile: $originalProfile)")
            handler.post(checkForegroundAppRunnable)
        }
    }

    private fun stopMonitoring() {
        if (isMonitoring) {
            isMonitoring = false
            handler.removeCallbacks(checkForegroundAppRunnable)
            
            synchronized(this) {
                pendingSwitchRunnable?.let { switchHandler.removeCallbacks(it) }
                pendingSwitchRunnable = null
            }
            
            if (hasOriginalProfile && originalProfile >= 0) {
                DolbyConstants.dlog(TAG, "Restoring original profile: $originalProfile")
                dolbyRepository.setCurrentProfile(originalProfile)
                
                val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
                val currentProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
                if (currentProfile != originalProfile) {
                    DolbyConstants.dlog(TAG, "WARNING: Profile restoration mismatch! Expected: $originalProfile, Got: $currentProfile")
                } else {
                    DolbyConstants.dlog(TAG, "Profile restored successfully")
                }
            } else {
                DolbyConstants.dlog(TAG, "No valid original profile to restore (hasOriginal=$hasOriginalProfile, profile=$originalProfile)")
            }
            
            DolbyConstants.dlog(TAG, "Stopped monitoring foreground app")
        }
    }

    private fun isHeadphoneConnected(): Boolean {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking headphone connection", e)
            false
        }
    }

    private fun checkForegroundApp() {
        try {
            val prefs = getSharedPreferences("dolby_prefs", Context.MODE_PRIVATE)
            val headphoneOnlyMode = prefs.getBoolean("app_profile_headphone_only", false)
            
            if (headphoneOnlyMode && !isHeadphoneConnected()) {
                DolbyConstants.dlog(TAG, "Headphone-only mode enabled but no headphones connected, skipping profile switch")
                return
            }
            
            val packageName = getForegroundPackage() ?: return
            
            val previousPackage = lastPackageName.getAndSet(packageName)
            if (packageName == previousPackage) {
                return
            }
            
            DolbyConstants.dlog(TAG, "Foreground app changed: $previousPackage -> $packageName")
            
            synchronized(this) {
                pendingSwitchRunnable?.let { switchHandler.removeCallbacks(it) }
                
                pendingSwitchRunnable = Runnable {
                    synchronized(this) {
                        try {
                            if (headphoneOnlyMode && !isHeadphoneConnected()) {
                                DolbyConstants.dlog(TAG, "Headphones disconnected, aborting profile switch")
                                return@Runnable
                            }
                            
                            val assignedProfile = appProfileManager.getAppProfile(packageName)
                            val showToasts = prefs.getBoolean("app_profile_show_toasts", true)
                            
                            if (assignedProfile >= 0) {
                                DolbyConstants.dlog(TAG, "Switching to profile $assignedProfile for $packageName")
                                lastProfileChangeTime = System.currentTimeMillis()
                                dolbyRepository.setCurrentProfile(assignedProfile)
                                DolbyConstants.dlog(TAG, "App profile active - original profile remains: $originalProfile")
                                
                                if (showToasts) {
                                    val profileName = getProfileName(assignedProfile)
                                    val appName = getAppName(packageName)
                                    ToastHelper.showToast(
                                        this@AppProfileMonitorService,
                                        "Dolby: $profileName ($appName)"
                                    )
                                }
                            } else {
                                if (hasOriginalProfile && originalProfile >= 0) {
                                    val currentProfile = prefs.getString(DolbyConstants.PREF_PROFILE, "0")?.toIntOrNull() ?: 0
                                    
                                    if (currentProfile != originalProfile) {
                                        DolbyConstants.dlog(TAG, "Restoring original profile $originalProfile for $packageName (current: $currentProfile)")
                                        lastProfileChangeTime = System.currentTimeMillis()
                                        dolbyRepository.setCurrentProfile(originalProfile)
                                    } else {
                                        DolbyConstants.dlog(TAG, "Already on original profile $originalProfile, no change needed")
                                    }
                                } else {
                                    DolbyConstants.dlog(TAG, "No original profile to restore (hasOriginal=$hasOriginalProfile, profile=$originalProfile)")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error switching profile", e)
                        } finally {
                            pendingSwitchRunnable = null
                        }
                    }
                }
                
                switchHandler.postDelayed(pendingSwitchRunnable!!, SWITCH_DELAY)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    private fun getForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        
        @Suppress("DEPRECATION")
        val usageEvents = usageStatsManager.queryEvents(currentTime - 1000, currentTime)
        val event = UsageEvents.Event()
        
        var lastPackage: String? = null
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            @Suppress("DEPRECATION")
            val movedToForeground = (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
            if (movedToForeground) {
                lastPackage = event.packageName
            }
        }
        
        return lastPackage
    }
    
    private fun getProfileName(profile: Int): String {
        val profiles = resources.getStringArray(R.array.dolby_profile_entries)
        val profileValues = resources.getStringArray(R.array.dolby_profile_values)
        
        return try {
            val index = profileValues.indexOfFirst { it.toInt() == profile }
            if (index >= 0) profiles[index] else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        DolbyConstants.dlog(TAG, "Service destroyed")
        stopMonitoring()
        dolbyRepository.close()
        hasOriginalProfile = false
    }

    companion object {
        private const val TAG = "AppProfileMonitor"
        private const val CHECK_INTERVAL = 2000L
        private const val SWITCH_DELAY = 300L
        
        const val ACTION_START_MONITORING = "org.lunaris.dolby.START_MONITORING"
        const val ACTION_STOP_MONITORING = "org.lunaris.dolby.STOP_MONITORING"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, AppProfileMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startService(intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, AppProfileMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }
}
