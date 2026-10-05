/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetsnack.ui.ar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.jetsnack.R

private const val SCENE_VIEWER_URL = "https://arvr.google.com/scene-viewer/1.0"
private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
private const val ARCORE_PACKAGE = "com.google.ar.core"

/**
 * Opens [modelUrl] in Google's Scene Viewer, preferring AR and falling back to the 3D viewer on
 * devices without ARCore support.
 *
 * See https://developers.google.com/ar/develop/scene-viewer
 */
fun launchSceneViewer(context: Context, modelUrl: String, title: String) {
    val uri = Uri.parse(SCENE_VIEWER_URL).buildUpon()
        .appendQueryParameter("file", modelUrl)
        .appendQueryParameter("mode", "ar_preferred")
        .appendQueryParameter("title", title)
        .build()

    // The Google app falls back to 3D mode gracefully; ARCore alone is the AR-only fallback.
    for (pkg in listOf(GOOGLE_APP_PACKAGE, ARCORE_PACKAGE)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next viewer.
        }
    }
    Toast.makeText(context, R.string.ar_unavailable, Toast.LENGTH_SHORT).show()
}
