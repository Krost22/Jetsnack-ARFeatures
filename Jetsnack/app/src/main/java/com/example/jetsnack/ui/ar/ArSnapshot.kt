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

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import com.example.jetsnack.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Grabs the current AR frame (camera feed + snacks, without any app UI on top) from the
 * TextureView the AR scene renders into. Must be called on the main thread.
 */
fun captureArFrame(root: View): Bitmap? = root.findTextureView()?.takeIf { it.isAvailable }?.bitmap

internal fun View.findTextureView(): TextureView? {
    if (this is TextureView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) getChildAt(i).findTextureView()?.let { return it }
    }
    return null
}

/** Stamps the Jetsnack name on [frame], saves it to the cache and opens the share sheet. */
suspend fun shareArSnapshot(context: Context, frame: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        val branded = frame.withWatermark(context.getString(R.string.app_name))
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "jetsnack_ar_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { branded.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.ar_share_text))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.ar_share_chooser)))
}

private fun Bitmap.withWatermark(text: String): Bitmap {
    val out = copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = out.width * 0.05f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        setShadowLayer(out.width * 0.006f, 0f, 0f, Color.argb(160, 0, 0, 0))
    }
    val margin = out.width * 0.04f
    canvas.drawText(text, margin, out.height - margin, paint)
    return out
}

private const val SHARED_DIR = "shared"
