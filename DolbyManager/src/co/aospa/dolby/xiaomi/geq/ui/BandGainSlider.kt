/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.aospa.dolby.xiaomi.geq.data.BandGain

@Composable
fun BandGainSlider(
    bandGain: BandGain,
    onValueChangeFinished: (Int) -> Unit
) {
    // Gain range is of -1->1 in UI, -100->100 in backend, but actually is -10->10 dB.

    // Ensure we update the slider when gain is changed,
    // for eg. when changing the preset
    var sliderPosition by remember(bandGain.gain) {
        mutableFloatStateOf(bandGain.gain / 100f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SliderText(
            "%.1f".format(sliderPosition * 10f)
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                onValueChangeFinished((sliderPosition * 100f).toInt())
            },
            valueRange = -1f..1f,
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxHeight,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
                // horizontal and vertical dimensions are inverted due to rotation
                .width(200.dp)
                .height(40.dp)
                .padding(8.dp)
        )
        SliderText(
            with(bandGain.band) {
                if (this >= 1000) {
                    "${this / 1000}k"
                } else {
                    "$this"
                }
            }
        )
    }
}

@Composable
fun SliderText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp
    )
}
