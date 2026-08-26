/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Contributor(
    val name: String,
    val githubUsername: String,
    val contribution: String,
    val isHighlighted: Boolean = false
)

data class Translator(
    val name: String,
    val githubUsername: String
)

data class TranslationEntry(
    val language: String,
    val translators: List<Translator>
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreditsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    val repoUrl = "https://github.com/Pong-Development/hardware_dolby"
    
    val mainContributors = listOf(
        Contributor(
            name = "Ghost",
            githubUsername = "Ghosuto",
            contribution = "Rewrite Dolby in Compose (Lunaris Dolby)",
            isHighlighted = true
        ),
        Contributor(
            name = "Adithya R",
            githubUsername = "adithya2306",
            contribution = "AOSPA Dolby Manager (Initial Code)",
            isHighlighted = true
        ),
        Contributor(
            name = "Kenway",
            githubUsername = "kenway214",
            contribution = "Base & Treble Changes, EQ Tuning",
            isHighlighted = true
        ),
        Contributor(
            name = "tranQuila",
            githubUsername = "MrTopia",
            contribution = "Adding per-device dolby state memory",
            isHighlighted = true
        )
    )
    
    val translationEntries = listOf(
        TranslationEntry(
            language = "Spanish (Spain)",
            translators = listOf(
                Translator(name = "sm6150-dreams", githubUsername = "sm6150-dreams")
            )
        ),
        TranslationEntry(
            language = "Portuguese (Brazil)",
            translators = listOf(
                Translator(name = "SMarcosS", githubUsername = "S-Marcos-S")
            )
        ),
        TranslationEntry(
            language = "Indonesian",
            translators = listOf(
                Translator(name = "Alhaidar Latif", githubUsername = "zylhdrXP")
            )
        ),
        TranslationEntry(
            language = "Polish",
            translators = listOf(
                Translator(name = "Kacper", githubUsername = "ziomek3120"),
                Translator(name = "rehork", githubUsername = "rehork")
            )
        ),
        TranslationEntry(
            language = "Traditional Chinese",
            translators = listOf(
                Translator(name = "DenlNister", githubUsername = "nnn950711")
            )
        ),
        TranslationEntry(
            language = "Turkish",
            translators = listOf(
                Translator(name = "Ümit Taylan", githubUsername = "jinetty")
            )
        ),
        TranslationEntry(
            language = "Russian",
            translators = listOf(
                Translator(name = "Dmitry", githubUsername = "dkpost3")
            )
        )
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Credits & Contributors",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Thank you to all contributors who made this project possible!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl))
                                    context.startActivity(intent)
                                },
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "View on GitHub",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "hardware_dolby",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Main Contributors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(mainContributors) { contributor ->
                        ContributorCard(contributor = contributor)
                    }
                    item {
                        Text(
                            text = "Translation Contributors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(translationEntries.chunked(2)) { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowEntries.forEach { entry ->
                                Box(modifier = Modifier.weight(1f)) {
                                    CompactTranslationCard(entry = entry)
                                }
                            }
                            if (rowEntries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Translate,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Missing your language?",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Help us make Lunaris Dolby better in your language! Submit or update translations on GitHub.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$repoUrl/pulls"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Submit a Pull Request")
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "And all other contributors",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Check the GitHub repository for the complete list",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorCard(
    contributor: Contributor
) {
    val context = LocalContext.current
    val githubUrl = "https://github.com/${contributor.githubUsername}"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                context.startActivity(intent)
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (contributor.isHighlighted)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GithubAvatar(
                username = contributor.githubUsername,
                modifier = Modifier.size(48.dp),
                iconSize = 28.dp,
                shape = MaterialTheme.shapes.medium,
                isHighlighted = contributor.isHighlighted
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contributor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@${contributor.githubUsername}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contributor.contribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CompactTranslationCard(
    entry: TranslationEntry
) {
    val context = LocalContext.current
    val translators = entry.translators
    var currentIndex by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(translators.size) {
        if (translators.size > 1) {
            while (isActive) {
                delay(3500)
                currentIndex = (currentIndex + 1) % translators.size
            }
        }
    }
    
    val currentTranslator = translators.getOrElse(currentIndex) { translators.first() }
    val githubUrl = "https://github.com/${currentTranslator.githubUsername}"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                context.startActivity(intent)
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StackedAvatars(
                    translators = translators,
                    currentIndex = currentIndex
                )
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AnimatedContent(
                targetState = currentTranslator,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn(animationSpec = tween(300)))
                        .togetherWith(slideOutVertically { height -> -height } + fadeOut(animationSpec = tween(300)))
                },
                label = "TranslatorInfoAnimation"
            ) { target ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${target.githubUsername}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = entry.language,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StackedAvatars(
    translators: List<Translator>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val count = translators.size
    val avatarSize = 36.dp
    val stepSpacing = if (count <= 1) {
        0.dp
    } else {
        val maxAvailableSpread = 44.dp
        val calculated = maxAvailableSpread / (count - 1)
        if (calculated < 18.dp) calculated else 18.dp
    }
    val totalWidth = avatarSize + (stepSpacing * (count - 1).coerceAtLeast(0))

    Box(
        modifier = modifier
            .height(avatarSize)
            .width(totalWidth),
        contentAlignment = Alignment.CenterStart
    ) {
        translators.forEachIndexed { index, translator ->
            val isActive = index == currentIndex
            val animatedScale by animateFloatAsState(
                targetValue = if (isActive || count == 1) 1f else 0.8f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "avatarScale_$index"
            )
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isActive || count == 1) 1f else 0.65f,
                animationSpec = tween(300),
                label = "avatarAlpha_$index"
            )
            val zIndex = if (isActive) 10f else (count - index).toFloat()
            val offsetX = stepSpacing * index

            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .zIndex(zIndex)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        alpha = animatedAlpha
                    }
                    .border(
                        width = if (count > 1) 1.5.dp else 0.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                GithubAvatar(
                    username = translator.githubUsername,
                    modifier = Modifier.size(avatarSize),
                    iconSize = 20.dp,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    }
}

private val avatarCache = LruCache<String, Bitmap>(30)

private suspend fun fetchGithubAvatar(username: String): Bitmap? = withContext(Dispatchers.IO) {
    if (username.isBlank()) return@withContext null
    avatarCache.get(username)?.let { return@withContext it }
    
    var connection: HttpURLConnection? = null
    try {
        val url = URL("https://github.com/$username.png?size=96")
        connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Lunaris-Dolby/1.0")
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    avatarCache.put(username, bitmap)
                    bitmap
                } else null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

@Composable
private fun GithubAvatar(
    username: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    shape: Shape = MaterialTheme.shapes.medium,
    isHighlighted: Boolean = false
) {
    val bitmapState = produceState<Bitmap?>(initialValue = avatarCache.get(username), username) {
        if (value == null) {
            value = fetchGithubAvatar(username)
        }
    }
    
    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape)
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = if (isHighlighted)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isHighlighted)
                        MaterialTheme.colorScheme.onTertiary
                    else
                        MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}


