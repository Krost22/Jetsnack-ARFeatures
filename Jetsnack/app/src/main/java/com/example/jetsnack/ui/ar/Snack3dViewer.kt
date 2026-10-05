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

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.example.jetsnack.model.DonutStyle
import com.example.jetsnack.model.SnackArModel
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberModelInstance

/**
 * Interactive 3D turntable for a snack: it slowly spins on its own and can be orbited and zoomed
 * with gestures. The background is transparent so it blends with the screen behind it.
 *
 * @param paused stops rendering (and spinning) while the viewer is not visible.
 */
@Composable
fun Snack3dViewer(
    model: SnackArModel,
    donutStyle: DonutStyle,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    onModelLoaded: () -> Unit = {},
) {
    val runtime = rememberSnackSceneRuntime()
    val cameraManipulator = remember {
        createDefaultCameraManipulator(
            eyePosition = Position(x = 0f, y = 0.75f, z = 1.45f),
            targetPosition = Position(0f),
        )
    }
    // Spun from the render loop instead of an animated Compose state, so turning the model does
    // not recompose the scene on every frame.
    val spinningNode = remember { arrayOfNulls<ModelNode>(1) }
    val rootView = LocalView.current
    val modelVisible = remember { booleanArrayOf(false) }
    val framesChecked = remember { intArrayOf(0) }
    SceneView(
        modifier = modifier,
        // A TextureView so the scene can be faded and scaled with the collapsing header.
        surfaceType = SurfaceType.TextureSurface,
        engine = runtime.engine,
        modelLoader = runtime.modelLoader,
        materialLoader = runtime.materialLoader,
        isOpaque = false,
        frameRatePolicy = if (paused) FrameRatePolicy.OnDemand() else FrameRatePolicy.Continuous(),
        cameraManipulator = cameraManipulator,
        onFrame = { frameTimeNanos ->
            spinningNode[0]?.let { node ->
                val periodNanos = TurntablePeriodMillis * 1_000_000L
                node.rotation = Rotation(y = (frameTimeNanos % periodNanos) * 360f / periodNanos)
                // Frames keep being presented while the GPU compiles the model's shaders (seconds
                // the first time), so "a frame was drawn" does not mean the model is visible. Peek
                // at a few pixels of the surface instead and only reveal the 3D view once they are.
                if (!modelVisible[0] && framesChecked[0]++ % VisibilityCheckInterval == 0) {
                    if (rootView.findTextureView()?.hasVisiblePixels() == true) {
                        modelVisible[0] = true
                        onModelLoaded()
                    }
                }
            }
        },
    ) {
        rememberModelInstance(runtime.modelLoader, model.assetPath)?.let { instance ->
            SnackModelNode(
                instance = instance,
                customizable = model.customizable,
                donutStyle = donutStyle,
                scaleToUnits = 1f,
                centerOrigin = Position(0f),
                onNodeReady = { node -> spinningNode[0] = node },
            )
        }
    }
}

private const val TurntablePeriodMillis = 14_000L

private const val VisibilityCheckInterval = 6

/** Samples the surface at a tiny size and checks whether anything opaque has been drawn yet. */
private fun TextureView.hasVisiblePixels(): Boolean {
    val sample = getBitmap(VisibilitySampleSize, VisibilitySampleSize) ?: return false
    val pixels = IntArray(VisibilitySampleSize * VisibilitySampleSize)
    sample.getPixels(pixels, 0, VisibilitySampleSize, 0, 0, VisibilitySampleSize, VisibilitySampleSize)
    sample.recycle()
    return pixels.count { (it ushr 24) > 0 } > pixels.size / 20
}

private const val VisibilitySampleSize = 16
