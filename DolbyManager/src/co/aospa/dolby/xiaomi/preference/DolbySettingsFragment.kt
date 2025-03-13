/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.preference

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragment
import androidx.preference.SwitchPreferenceCompat
import co.aospa.dolby.xiaomi.DolbyConstants
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_ENABLE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PRESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PROFILE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_RESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_STEREO
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.widget.MainSwitchPreference

class DolbySettingsFragment : PreferenceFragment(),
    OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {

    private val switchBar by lazy {
        findPreference<MainSwitchPreference>(PREF_ENABLE)!!
    }
    private val profilePref by lazy {
        findPreference<ListPreference>(PREF_PROFILE)!!
    }
    private val presetPref by lazy {
        findPreference<Preference>(PREF_PRESET)!!
    }
    private val ieqPref by lazy {
        findPreference<DolbyIeqPreference>(PREF_IEQ)!!
    }
    private val stereoPref by lazy {
        findPreference<ListPreference>(PREF_STEREO)!!
    }
    private val dialoguePref by lazy {
        findPreference<ListPreference>(PREF_DIALOGUE)!!
    }
    private val bassPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_BASS)!!
    }
    private val hpVirtPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_HP_VIRTUALIZER)!!
    }
    private val spkVirtPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_SPK_VIRTUALIZER)!!
    }
    private val volumePref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_VOLUME)!!
    }
    private val resetPref by lazy {
        findPreference<Preference>(PREF_RESET)!!
    }

    private val dolbyController by lazy { DolbyController.getInstance(context) }
    private val audioManager by lazy { context.getSystemService(AudioManager::class.java) }
    private val handler = Handler()

    private var isOnSpeaker = true
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setIsOnSpeaker($value)")
            updateProfileSpecificPrefs()
        }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            updateSpeakerState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            updateSpeakerState()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        dlog(TAG, "onCreatePreferences")
        addPreferencesFromResource(R.xml.dolby_settings)

        val profile = dolbyController.profile
        preferenceManager.preferenceDataStore = DolbyPreferenceStore(context).also {
            it.profile = profile
        }

        val dsOn = dolbyController.dsOn
        switchBar.addOnSwitchChangeListener(this)
        switchBar.setChecked(dsOn)

        profilePref.onPreferenceChangeListener = this
        updateProfileIcon(profile)
        profilePref.setEnabled(dsOn)
        profilePref.apply {
            if (entryValues.contains(profile.toString())) {
                summary = "%s"
                value = profile.toString()
            } else {
                summary = context.getString(R.string.dolby_unknown)
            }
        }

        hpVirtPref.onPreferenceChangeListener = this
        spkVirtPref.onPreferenceChangeListener = this
        stereoPref.onPreferenceChangeListener = this
        dialoguePref.onPreferenceChangeListener = this
        bassPref.onPreferenceChangeListener = this
        volumePref.onPreferenceChangeListener = this
        ieqPref.onPreferenceChangeListener = this

        resetPref.setOnPreferenceClickListener {
            dolbyController.resetProfileSpecificSettings()
            updateProfileSpecificPrefs()
            Toast.makeText(
                context,
                context.getString(R.string.dolby_reset_profile_toast, profilePref.summary),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        audioManager!!.registerAudioDeviceCallback(audioDeviceCallback, handler)
        updateSpeakerState()
        updateProfileSpecificPrefs()
    }

    override fun onDestroyView() {
        dlog(TAG, "onDestroyView")
        audioManager!!.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateProfileSpecificPrefs()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        dlog(TAG, "onPreferenceChange: key=${preference.key} value=$newValue")
        when (preference.key) {
            PREF_PROFILE -> {
                val profile = newValue.toString().toInt()
                dolbyController.profile = profile
                (preferenceManager.preferenceDataStore as DolbyPreferenceStore).profile = profile
                updateProfileIcon(profile)
                updateProfileSpecificPrefs()
            }

            PREF_SPK_VIRTUALIZER -> {
                dolbyController.setSpeakerVirtEnabled(newValue as Boolean)
            }

            PREF_HP_VIRTUALIZER -> {
                dolbyController.setHeadphoneVirtEnabled(newValue as Boolean)
            }

            PREF_STEREO -> {
                dolbyController.setStereoWideningAmount(newValue.toString().toInt())
            }

            PREF_DIALOGUE -> {
                dolbyController.setDialogueEnhancerAmount(newValue.toString().toInt())
            }

            PREF_BASS -> {
                dolbyController.setBassEnhancerEnabled(newValue as Boolean)
            }

            PREF_VOLUME -> {
                dolbyController.setVolumeLevelerEnabled(newValue as Boolean)
            }

            PREF_IEQ -> {
                dolbyController.setIeqPreset(newValue.toString().toInt())
            }

            else -> return false
        }
        return true
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        dlog(TAG, "onCheckedChanged($isChecked)")
        dolbyController.dsOn = isChecked
        profilePref.setEnabled(isChecked)
        updateProfileSpecificPrefs()
    }

    private fun updateSpeakerState() {
        val device = audioManager!!.getDevicesForAttributes(ATTRIBUTES_MEDIA)[0]
        isOnSpeaker = (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    }

    private fun updateProfileSpecificPrefs() {
        val unknownRes = context.getString(R.string.dolby_unknown)
        val headphoneRes = context.getString(R.string.dolby_connect_headphones)
        val dsOn = dolbyController.dsOn
        val currentProfile = dolbyController.profile

        dlog(
            TAG, "updateProfileSpecificPrefs: dsOn=$dsOn currentProfile=$currentProfile"
                    + " isOnSpeaker=$isOnSpeaker"
        )

        val enable = dsOn && (currentProfile != -1)
        presetPref.setEnabled(enable)
        spkVirtPref.setEnabled(enable)
        ieqPref.setEnabled(enable)
        dialoguePref.setEnabled(enable)
        volumePref.setEnabled(enable)
        bassPref.setEnabled(enable)
        resetPref.setEnabled(enable)
        hpVirtPref.setEnabled(enable && !isOnSpeaker)
        stereoPref.setEnabled(enable && !isOnSpeaker)

        if (!enable) return

        presetPref.summary = dolbyController.getPresetName()

        val ieqValue = dolbyController.getIeqPreset(currentProfile)
        ieqPref.apply {
            if (entryValues.contains(ieqValue.toString())) {
                summary = "%s"
                value = ieqValue.toString()
            } else {
                summary = unknownRes
            }
        }

        val deValue = dolbyController.getDialogueEnhancerAmount(currentProfile).toString()
        dialoguePref.apply {
            if (entryValues.contains(deValue)) {
                summary = "%s"
                value = deValue
            } else {
                summary = unknownRes
            }
        }

        spkVirtPref.setChecked(dolbyController.getSpeakerVirtEnabled(currentProfile))
        volumePref.setChecked(dolbyController.getVolumeLevelerEnabled(currentProfile))
        bassPref.setChecked(dolbyController.getBassEnhancerEnabled(currentProfile))

        // below prefs are not enabled on loudspeaker
        if (isOnSpeaker) {
            stereoPref.summary = headphoneRes
            hpVirtPref.summary = headphoneRes
            return
        }

        val swValue = dolbyController.getStereoWideningAmount(currentProfile).toString()
        stereoPref.apply {
            if (entryValues.contains(swValue)) {
                summary = "%s"
                value = swValue
            } else {
                summary = unknownRes
            }
        }

        hpVirtPref.apply {
            setChecked(dolbyController.getHeadphoneVirtEnabled(currentProfile))
            summary = null
        }
    }

    private fun updateProfileIcon(profile: Int) {
        when (profile) {
            0 -> profilePref.setIcon(R.drawable.ic_profile_dynamic)
            1 -> profilePref.setIcon(R.drawable.ic_profile_movie)
            2 -> profilePref.setIcon(R.drawable.ic_profile_music)
            3 -> profilePref.setIcon(R.drawable.ic_profile_custom)
            else -> profilePref.setIcon(R.drawable.ic_dolby)
        }
    }

    companion object {
        private const val TAG = "DolbySettingsFragment"
        private val ATTRIBUTES_MEDIA = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
}
