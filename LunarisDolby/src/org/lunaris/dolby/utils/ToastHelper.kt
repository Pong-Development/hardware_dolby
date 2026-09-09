/*
 * Copyright (C) 2024-2025 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.dolby.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null
    
    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        mainHandler.post {
            currentToast?.cancel()
            currentToast = Toast.makeText(context, message, duration)
            currentToast?.show()
        }
    }
    
    fun showLongToast(context: Context, message: String) {
        showToast(context, message, Toast.LENGTH_LONG)
    }
}
