/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.aospa.dolby.xiaomi.R

@Composable
fun BandGainSliderLabels() {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        LabelText(
            stringResource(id = R.string.dolby_geq_slider_label_gain)
        )
        Column(
            modifier = Modifier.height(200.dp),
            horizontalAlignment = Alignment.End
        ) {
            LabelText(
                "+10 dB",
                modifier = Modifier.padding(
                    top = 10.dp
                )
            )
            Spacer(
                modifier = Modifier.weight(1f)
            )
            LabelText("0 dB")
            Spacer(
                modifier = Modifier.weight(1f)
            )
            LabelText(
                "-10 dB",
                modifier = Modifier.padding(
                    bottom = 10.dp
                )
            )
        }
        LabelText("Hz")
    }
}

@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 12.sp
    )
}
