/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import co.aospa.dolby.xiaomi.R

// Preference with icon on the right side
class DolbyIeqPreference(
    context: Context,
    attrs: AttributeSet?,
) : ListPreference(context, attrs) {

    init {
        widgetLayoutResource = R.layout.ieq_icon_layout
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val iconView = holder.findViewById(R.id.ieq_icon)!! as ImageView
        val icon = AppCompatResources.getDrawable(context, getIeqIconResId())
        iconView.setImageDrawable(icon)
    }

    private fun getIeqIconResId(): Int {
        val ieqValue = value?.toIntOrNull() ?: 0
        return when (ieqValue) {
            0 -> R.drawable.ic_ieq_off
            1 -> R.drawable.ic_ieq_balanced
            2 -> R.drawable.ic_ieq_warm
            3 -> R.drawable.ic_ieq_detailed
            else -> 0 // should never hit this!
        }
    }
}
