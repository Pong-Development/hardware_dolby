/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.data.autoeq

import android.content.Context
import android.net.http.HttpResponseCache
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object AutoEqConfig {
    const val BASE_URL = "https://raw.githubusercontent.com/aosp-pablo/dolby_profiles/main"
    const val METADATA = "$BASE_URL/metadata.json"
    const val INDEX = "$BASE_URL/index.json.gz"
    fun profile(id: String) = "$BASE_URL/profiles/$id.json.gz"
    
    const val TAG = "AutoEqNetwork"
    const val USER_AGENT = "Lunaris-Dolby-AutoEQ/1.0"
}

class AutoEqDownloader(context: Context) {

    init {
        try {
            val cacheDir = File(context.cacheDir, "autoeq_http")
            val cacheSize = 20L * 1024 * 1024 // 20 MB cache
            if (HttpResponseCache.getInstalled() == null) {
                HttpResponseCache.install(cacheDir, cacheSize)
            }
        } catch (e: Exception) {
            Log.w(AutoEqConfig.TAG, "Failed to install native HTTP cache", e)
        }
    }

    suspend fun fetchString(
        urlString: String, 
        isGzipped: Boolean = false, 
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            Log.d(AutoEqConfig.TAG, "Fetching: $urlString")
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", AutoEqConfig.USER_AGENT)

            if (forceRefresh) {
                connection.setRequestProperty("Cache-Control", "no-cache")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.w(AutoEqConfig.TAG, "HTTP $responseCode for URL: $urlString")
                return@withContext null
            }

            val stream = connection.inputStream ?: return@withContext null

            if (isGzipped) {
                GZIPInputStream(stream).bufferedReader().use { it.readText() }
            } else {
                stream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(AutoEqConfig.TAG, "Network request failed for URL: $urlString", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}

class AutoEqApi(private val downloader: AutoEqDownloader) {

    suspend fun getMetadata(): String? = 
        downloader.fetchString(
            urlString = AutoEqConfig.METADATA, 
            forceRefresh = true 
        )

    suspend fun getIndex(): String? = 
        downloader.fetchString(
            urlString = AutoEqConfig.INDEX, 
            isGzipped = true
        )

    suspend fun getProfile(id: String): String? = 
        downloader.fetchString(
            urlString = AutoEqConfig.profile(id), 
            isGzipped = true
        )
}
