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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.jetsnack.model.DonutStyle
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode

/**
 * Adds a loaded snack model to the scene. Customizable models (the donut) are restyled in place
 * whenever [donutStyle] changes; [onNodeReady] hands out the node once it exists.
 */
@Composable
fun SceneScope.SnackModelNode(
    instance: ModelInstance,
    customizable: Boolean,
    donutStyle: DonutStyle,
    position: Position = Position(0f),
    rotation: Rotation = Rotation(0f),
    scaleToUnits: Float? = null,
    centerOrigin: Position? = null,
    onNodeReady: (ModelNode) -> Unit = {},
) {
    var node by remember(instance) { mutableStateOf<ModelNode?>(null) }
    ModelNode(
        modelInstance = instance,
        scaleToUnits = scaleToUnits,
        centerOrigin = centerOrigin,
        position = position,
        rotation = rotation,
        apply = {
            isShadowCaster = true
            node = this
        },
    )
    LaunchedEffect(node) { node?.let(onNodeReady) }
    LaunchedEffect(node, donutStyle) {
        if (customizable) node?.applyDonutStyle(donutStyle)
    }
}

/** Drives the donut model's named nodes (see `generate-models.mjs`). */
fun ModelNode.applyDonutStyle(style: DonutStyle) {
    renderableNodes.forEach { node ->
        when (node.name) {
            "Icing" -> node.materialInstance.setBaseColor(style.glaze.linearColor)

            "Sprinkles" -> node.isVisible = style.sprinkles

            "Drizzle" -> {
                node.isVisible = style.drizzle
                node.materialInstance.setBaseColor(style.glaze.drizzleLinearColor)
            }
        }
    }
}

private fun MaterialInstance.setBaseColor(linearRgb: FloatArray) {
    setParameter("baseColorFactor", linearRgb[0], linearRgb[1], linearRgb[2], 1f)
}
