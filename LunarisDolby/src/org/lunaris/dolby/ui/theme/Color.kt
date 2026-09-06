/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Material 3 Expressive tonal palettes for Lunaris Dolby.
 *
 * These are the non-dynamic fallback schemes, used when the platform cannot
 * supply system accents. Source colours:
 *   primary   - electric indigo, the app accent
 *   secondary - desaturated slate violet, for supporting surfaces
 *   tertiary  - warm amber, the Expressive contrast accent
 *   neutral   - violet-tinted grey, so surfaces stay related to the accent
 */

// Primary - electric indigo
private val Indigo10 = Color(0xFF0A0A5C)
private val Indigo20 = Color(0xFF212490)
private val Indigo30 = Color(0xFF3639B4)
private val Indigo40 = Color(0xFF4B4FCF)
private val Indigo80 = Color(0xFFC0C1FF)
private val Indigo90 = Color(0xFFE2E0FF)

// Secondary - slate violet
private val Slate10 = Color(0xFF191A2C)
private val Slate20 = Color(0xFF2E2F42)
private val Slate30 = Color(0xFF444559)
private val Slate40 = Color(0xFF5C5D72)
private val Slate80 = Color(0xFFC5C4DD)
private val Slate90 = Color(0xFFE1E0F9)

// Tertiary - warm amber
private val Amber10 = Color(0xFF271900)
private val Amber20 = Color(0xFF402D00)
private val Amber30 = Color(0xFF5C4200)
private val Amber40 = Color(0xFF7A5900)
private val Amber80 = Color(0xFFF5BF48)
private val Amber90 = Color(0xFFFFDF9B)

// Error - Material baseline red
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// Neutral / neutral variant - violet-tinted greys
private val NeutralWhite = Color(0xFFFFFFFF)
private val NeutralBlack = Color(0xFF000000)

private val LightSurface = Color(0xFFFCF8FF)
private val LightSurfaceDim = Color(0xFFDBD9E0)
private val LightOnSurface = Color(0xFF1B1B21)
private val LightSurfaceVariant = Color(0xFFE4E1EC)
private val LightOnSurfaceVariant = Color(0xFF46464F)
private val LightOutline = Color(0xFF777680)
private val LightOutlineVariant = Color(0xFFC7C5D0)
private val LightContainerLowest = Color(0xFFFFFFFF)
private val LightContainerLow = Color(0xFFF6F2FA)
private val LightContainer = Color(0xFFF0ECF4)
private val LightContainerHigh = Color(0xFFEAE7EF)
private val LightContainerHighest = Color(0xFFE4E1E9)
private val LightInverseSurface = Color(0xFF303036)
private val LightInverseOnSurface = Color(0xFFF2EFF7)

private val DarkSurface = Color(0xFF131318)
private val DarkSurfaceBright = Color(0xFF39383F)
private val DarkOnSurface = Color(0xFFE4E1E9)
private val DarkSurfaceVariant = Color(0xFF46464F)
private val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
private val DarkOutline = Color(0xFF91909A)
private val DarkOutlineVariant = Color(0xFF46464F)
private val DarkContainerLowest = Color(0xFF0E0E13)
private val DarkContainerLow = Color(0xFF1B1B21)
private val DarkContainer = Color(0xFF1F1F25)
private val DarkContainerHigh = Color(0xFF2A292F)
private val DarkContainerHighest = Color(0xFF35343A)

internal val DolbyLightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = NeutralWhite,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    inversePrimary = Indigo80,

    secondary = Slate40,
    onSecondary = NeutralWhite,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,

    tertiary = Amber40,
    onTertiary = NeutralWhite,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,

    error = Red40,
    onError = NeutralWhite,
    errorContainer = Red90,
    onErrorContainer = Red10,

    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Indigo40,
    surfaceBright = LightSurface,
    surfaceDim = LightSurfaceDim,
    surfaceContainerLowest = LightContainerLowest,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHighest,

    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = NeutralBlack
)

internal val DolbyDarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    inversePrimary = Indigo40,

    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,

    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,

    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Indigo80,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurface,
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,

    inverseSurface = DarkOnSurface,
    inverseOnSurface = LightInverseSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = NeutralBlack
)
