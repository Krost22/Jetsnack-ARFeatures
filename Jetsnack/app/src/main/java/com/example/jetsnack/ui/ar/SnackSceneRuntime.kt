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
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.Engine
import io.github.sceneview.createEglContext
import io.github.sceneview.createEngine
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.utils.readBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Filament engine and loaders shared by every 3D and AR screen.
 *
 * Creating them is the expensive part of opening a 3D screen: an EGL context, the engine and,
 * above all, compiling the glTF ubershader materials. Each `rememberEngine()` /
 * `rememberModelLoader()` would redo that work every time the snack detail or the AR screen opens,
 * so they are created once and kept for the life of the process. Screens still create (and
 * destroy) their own views, renderers, scenes and models.
 */
class SnackSceneRuntime private constructor(context: Context) {
    val engine: Engine = createEngine(createEglContext())
    val modelLoader = ModelLoader(engine, context)
    val materialLoader = MaterialLoader(engine, context)

    companion object {
        private var instance: SnackSceneRuntime? = null

        /** Must be called on the main thread, which owns the Filament engine. */
        @MainThread
        fun get(context: Context): SnackSceneRuntime = instance ?: SnackSceneRuntime(context.applicationContext).also { instance = it }
    }
}

@Composable
fun rememberSnackSceneRuntime(): SnackSceneRuntime {
    val context = LocalContext.current
    return remember { SnackSceneRuntime.get(context) }
}

/**
 * Parses [assetPath] once and creates [count] instances that share its GPU buffers, so several
 * copies of the same snack on the table cost a single load. The model is destroyed when it leaves
 * the composition.
 */
@Composable
fun rememberInstancedModels(modelLoader: ModelLoader, assetPath: String, count: Int): List<ModelInstance>? {
    val context = LocalContext.current
    val instances = produceState<List<ModelInstance>?>(initialValue = null, modelLoader, assetPath, count) {
        val buffer = withContext(Dispatchers.IO) {
            runCatching { context.assets.readBuffer(assetPath) }.getOrNull()
        } ?: return@produceState
        value = runCatching { modelLoader.createInstancedModel(buffer, count) }.getOrNull()
    }.value
    DisposableEffect(instances) {
        onDispose { instances?.firstOrNull()?.let { modelLoader.destroyModel(it.model) } }
    }
    return instances
}
