/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import org.lunaris.dolby.R
import org.lunaris.dolby.utils.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileCarousel(
    currentProfile: Int,
    onProfileChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    
    val profileIcons = mapOf(
        0 to Icons.Default.AutoAwesome,
        1 to Icons.Default.Movie,
        2 to Icons.Default.MusicNote,
        3 to Icons.Default.SportsEsports,
        4 to Icons.Default.Work,
        5 to Icons.Default.Coffee,
        6 to Icons.Default.Favorite
    )
    
    val profilePalettes = rememberProfilePalettes()

    val initialPage = profileValues.indexOfFirst { it.toInt() == currentProfile }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { profiles.size }
    )
    
    var lastPage by remember { mutableIntStateOf(initialPage) }
    
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
            lastPage = pagerState.currentPage
            
            if (pagerState.currentPage != initialPage) {
                val selectedValue = profileValues[pagerState.currentPage].toInt()
                onProfileChange(selectedValue)
            }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.dolby_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentPadding = PaddingValues(horizontal = 64.dp),
                pageSpacing = 8.dp
            ) { page ->
                val profileValue = profileValues[page].toInt()
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                
                ProfileCard(
                    profile = profiles[page],
                    icon = profileIcons[profileValue] ?: Icons.Default.Tune,
                    palette = profilePalettes.getOrElse(profileValue) { profilePalettes[0] },
                    isSelected = page == pagerState.currentPage,
                    pageOffset = pageOffset,
                    onClick = {
                        scope.launch {
                            haptic.performHaptic(HapticFeedbackHelper.HapticIntensity.CLICK)
                            pagerState.animateScrollToPage(page)
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(profiles.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(
                                width = if (isSelected) 24.dp else 6.dp,
                                height = 6.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

/**
 * A profile's colour pair, derived entirely from the active Material 3 scheme so
 * the carousel follows dynamic colour instead of fixed brand gradients.
 */
internal data class ProfilePalette(
    val start: Color,
    val end: Color,
    val content: Color
)

@Composable
private fun rememberProfilePalettes(): List<ProfilePalette> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        listOf(
            ProfilePalette(scheme.primary, blend(scheme.primary, scheme.secondary, 0.35f), scheme.onPrimary),
            ProfilePalette(scheme.primary, blend(scheme.primary, scheme.tertiary, 0.55f), scheme.onPrimary),
            ProfilePalette(scheme.tertiary, blend(scheme.tertiary, scheme.primary, 0.35f), scheme.onTertiary),
            ProfilePalette(scheme.tertiary, blend(scheme.tertiary, scheme.secondary, 0.55f), scheme.onTertiary),
            ProfilePalette(scheme.secondary, blend(scheme.secondary, scheme.primary, 0.45f), scheme.onSecondary),
            ProfilePalette(scheme.secondary, blend(scheme.secondary, scheme.tertiary, 0.55f), scheme.onSecondary),
            ProfilePalette(scheme.primary, blend(scheme.primary, scheme.tertiary, 0.3f), scheme.onPrimary)
        )
    }
}

private fun blend(from: Color, to: Color, fraction: Float): Color =
    lerpColor(from, to, fraction)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileCard(
    profile: String,
    icon: ImageVector,
    palette: ProfilePalette,
    isSelected: Boolean,
    pageOffset: Float,
    onClick: () -> Unit
) {
    val scale = lerp(
        start = 0.8f,
        stop = 1f,
        fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
    )
    
    val alpha = lerp(
        start = 0.5f,
        stop = 1f,
        fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
    )
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = palette.end,
            contentColor = palette.content
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.start, palette.end)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.85f,
                    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "icon_scale"
                )
                
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(iconScale),
                    shape = CircleShape,
                    color = palette.content.copy(alpha = 0.22f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = palette.content,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = profile,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.content,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (isSelected) {
                    Surface(
                        modifier = Modifier.height(2.dp).width(24.dp),
                        shape = CircleShape,
                        color = palette.content
                    ) {}
                }
            }
            
            if (isSelected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp),
                    shape = CircleShape,
                    color = palette.content
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = palette.start,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
