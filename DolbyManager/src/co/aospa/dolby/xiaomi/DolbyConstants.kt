/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.util.Log

class DolbyConstants {

    enum class DsParam(val id: Int, val length: Int = 1) {
        HEADPHONE_VIRTUALIZER(101),
        SPEAKER_VIRTUALIZER(102),
        VOLUME_LEVELER_ENABLE(103),
        IEQ_PRESET(104),
        DIALOGUE_ENHANCER_ENABLE(105),
        DIALOGUE_ENHANCER_AMOUNT(108),
        GEQ_BAND_GAINS(110, 20),
        BASS_ENHANCER_ENABLE(111),
        STEREO_WIDENING_AMOUNT(113);

        override fun toString(): String {
            return "${name}(${id})"
        }
    }

    companion object {
        const val TAG = "XiaomiDolby"
        const val PREF_ENABLE = "dolby_enable"
        const val PREF_PROFILE = "dolby_profile"
        const val PREF_PRESET = "dolby_preset"
        const val PREF_IEQ = "dolby_ieq"
        const val PREF_HP_VIRTUALIZER = "dolby_virtualizer"
        const val PREF_SPK_VIRTUALIZER = "dolby_spk_virtualizer"
        const val PREF_STEREO = "dolby_stereo"
        const val PREF_DIALOGUE = "dolby_dialogue"
        const val PREF_BASS = "dolby_bass"
        const val PREF_VOLUME = "dolby_volume"
        const val PREF_RESET = "dolby_reset"

        val PROFILE_SPECIFIC_PREFS = setOf(
            PREF_PRESET,
            PREF_IEQ,
            PREF_HP_VIRTUALIZER,
            PREF_SPK_VIRTUALIZER,
            PREF_STEREO,
            PREF_DIALOGUE,
            PREF_BASS,
            PREF_VOLUME
        )

        fun dlog(tag: String, msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(tag, msg)
            }
        }
    }
}
