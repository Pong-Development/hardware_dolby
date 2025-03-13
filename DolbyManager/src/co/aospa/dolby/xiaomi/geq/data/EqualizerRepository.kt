/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PRESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class EqualizerRepository(
    private val context: Context
) {

    private val dolbyController by lazy { DolbyController.getInstance(context) }

    // Preset is saved as a string of comma separated gains in SharedPreferences
    // and is unique to each profile ID
    private val profile = dolbyController.profile
    private val profileSharedPrefs by lazy {
        context.getSharedPreferences(
            "profile_$profile",
            Context.MODE_PRIVATE
        )
    }

    private val presetsSharedPrefs by lazy {
        context.getSharedPreferences(
            "presets",
            Context.MODE_PRIVATE
        )
    }

    val builtInPresets: List<Preset> by lazy {
        val names = context.resources.getStringArray(
            R.array.dolby_preset_entries
        )
        val presets = context.resources.getStringArray(
            R.array.dolby_preset_values
        )
        List(names.size) { index ->
            Preset(
                name = names[index],
                bandGains = deserializeGains(presets[index]),
            )
        }
    }

    val defaultPreset by lazy { builtInPresets[0] } // Flat

    // User defined presets are stored in a SharedPreferences as
    // key - preset name
    // value - comma separated string of gains
    val userPresets: Flow<List<Preset>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            dlog(TAG, "presetsSharedPrefs changed")
            trySend(
                presetsSharedPrefs.all.map { (key, value) ->
                    Preset(
                        name = key,
                        bandGains = deserializeGains(value.toString()),
                        isUserDefined = true
                    )
                }
            )
        }

        presetsSharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        dlog(TAG, "presetsSharedPrefs registered listener")
        // trigger an initial emission
        listener.onSharedPreferenceChanged(presetsSharedPrefs, null)

        awaitClose {
            presetsSharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            dlog(TAG, "presetsSharedPrefs unregistered listener")
        }
    }

    suspend fun getBandGains(): List<BandGain> = withContext(Dispatchers.IO) {
        val gains = profileSharedPrefs.getString(PREF_PRESET, dolbyController.getPreset())
        return@withContext if (gains.isNullOrEmpty()) {
            defaultPreset.bandGains
        } else {
            deserializeGains(gains)
        }.also {
            dlog(TAG, "getBandGains: $it")
        }
    }

    suspend fun setBandGains(bandGains: List<BandGain>) = withContext(Dispatchers.IO) {
        dlog(TAG, "setBandGains($bandGains)")
        val gains = serializeGains(bandGains)
        dolbyController.setPreset(gains)
        profileSharedPrefs.edit()
            .putString(PREF_PRESET, gains)
            .apply()
    }

    suspend fun addPreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "addPreset($preset)")
        presetsSharedPrefs.edit()
            .putString(preset.name, serializeGains(preset.bandGains))
            .apply()
    }

    suspend fun removePreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "removePreset($preset)")
        presetsSharedPrefs.edit()
            .remove(preset.name)
            .apply()
    }

    private companion object {
        const val TAG = "EqRepository"

        val tenBandFreqs = intArrayOf(
            32,
            64,
            125,
            250,
            500,
            1000,
            2000,
            4000,
            8000,
            16000
        )

        fun deserializeGains(bandGains: String): List<BandGain> {
            val gains: List<Int> =
                bandGains.split(",").runCatching {
                    require(size == 20) {
                        "Preset must have 20 elements, has only $size!"
                    }
                    map { it.toInt() }
                        .twentyToTenBandGains()
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to parse preset", exception)
                }.getOrDefault(
                    // fallback to flat
                    List<Int>(10) { 0 }
                )
            return List(10) { index ->
                BandGain(
                    band = tenBandFreqs[index],
                    gain = gains[index]
                )
            }
        }

        fun serializeGains(bandGains: List<BandGain>): String {
            return bandGains.map { it.gain }
                .tenToTwentyBandGains()
                .joinToString(",")
        }

        // we show only 10 bands in UI however backend requires 20 bands
        fun List<Int>.tenToTwentyBandGains() =
            List<Int>(20) { index ->
                if (index % 2 == 1 && index < 19) {
                    // every odd element is the average of its surrounding elements
                    (this[(index - 1) / 2] + this[(index + 1) / 2]) / 2
                } else {
                    this[index / 2]
                }
            }

        fun List<Int>.twentyToTenBandGains() =
            // skip every odd element
            filterIndexed { index, _ -> index % 2 == 0 }
    }
}
