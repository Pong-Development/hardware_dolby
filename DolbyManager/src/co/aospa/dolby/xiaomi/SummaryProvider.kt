/*
 * Copyright (C) 2019 The Android Open Source Project
 *           (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY

private const val KEY_DOLBY = "dolby"

/** Provide preference summary for injected items. */
class SummaryProvider : ContentProvider() {

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?
    ): Bundle? {
        val summary = when (method) {
            KEY_DOLBY -> getDolbySummary()
            else -> return null
        }
        return Bundle().apply {
            putString(META_DATA_PREFERENCE_SUMMARY, summary)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    private fun getDolbySummary(): String {
        val dolbyController = DolbyController.getInstance(context!!)
        if (!dolbyController.dsOn) {
            return context!!.getString(R.string.dolby_off)
        }
        return dolbyController.getProfileName()?.let {
            context!!.getString(R.string.dolby_on_with_profile, it)
        } ?: context!!.getString(R.string.dolby_on)
    }

}
