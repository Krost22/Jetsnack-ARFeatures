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

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.jetsnack.model.DonutStyle
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createDefaultCameraManipulator
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.utils.screenToRay

/**
 * True on the Android Emulator, whose virtual AR camera cannot track on every host. There the AR
 * screen shows [SimulatedTableScene] instead of the camera feed.
 */
fun isRunningOnEmulator(): Boolean = Build.HARDWARE == "ranchu" ||
    Build.HARDWARE == "goldfish" ||
    Build.FINGERPRINT.contains("generic") ||
    Build.PRODUCT.contains("sdk")

/**
 * A synthetic room (floor, wall, wooden table, a mug and a book) that stands in for the camera
 * feed when testing on the emulator. It has real depth: drag to walk around the table, pinch to
 * get closer, and tap the table top to place the snacks — the same layout, customization,
 * comparison and cart logic as the real AR view.
 *
 * @param placedAt where the snacks sit on the table top, or null before the first tap.
 */
@Composable
internal fun SimulatedTableScene(
    placements: List<Placement>,
    instancesPerSnack: Int,
    donutStyle: DonutStyle,
    placedAt: Position?,
    onPlaced: (Position) -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = rememberSnackSceneRuntime()
    val engine = runtime.engine
    val materialLoader = runtime.materialLoader
    val view = rememberView(engine)
    val cameraNode = rememberCameraNode(engine)
    // Looks down at the table from where a person holding a phone would stand.
    val cameraManipulator = remember {
        createDefaultCameraManipulator(
            eyePosition = Position(x = 0f, y = 0.62f, z = 0.62f),
            // Aimed a little in front of the table centre so the snacks sit above the bottom panel.
            targetPosition = Position(x = 0f, y = 0f, z = 0.12f),
        )
    }
    val materials = remember(materialLoader) {
        RoomMaterials(
            wood = materialLoader.createColorInstance(Color(0xFF8A5A36), roughness = 0.6f),
            woodDark = materialLoader.createColorInstance(Color(0xFF5C3A22), roughness = 0.7f),
            floor = materialLoader.createColorInstance(Color(0xFFBDB5AA), roughness = 0.9f),
            wall = materialLoader.createColorInstance(Color(0xFFEDE6DA), roughness = 1f),
            mug = materialLoader.createColorInstance(Color(0xFF3F6FB5), roughness = 0.3f),
            book = materialLoader.createColorInstance(Color(0xFFB23A3A), roughness = 0.8f),
            pages = materialLoader.createColorInstance(Color(0xFFF4F0E6), roughness = 1f),
        )
    }

    SceneView(
        modifier = modifier,
        // A TextureView, like the real AR view, so the share button can grab the frame.
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = runtime.modelLoader,
        materialLoader = materialLoader,
        view = view,
        cameraNode = cameraNode,
        autoCenterContent = false,
        frameRatePolicy = FrameRatePolicy.Continuous(),
        cameraManipulator = cameraManipulator,
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { event, _ ->
                tableHit(view.screenToRay(event.x, event.y))?.let(onPlaced)
            },
        ),
    ) {
        // Room.
        PlaneNode(size = Size(6f, 6f), materialInstance = materials.floor, position = Position(y = FloorY))
        CubeNode(
            size = Size(6f, 3f, 0.05f),
            materialInstance = materials.wall,
            position = Position(y = FloorY + 1.5f, z = -1.3f),
        )
        // Table: the top surface is y = 0, where the snacks rest.
        CubeNode(
            size = Size(TableWidth, TableThickness, TableDepth),
            materialInstance = materials.wood,
            position = Position(y = -TableThickness / 2),
        )
        for (x in listOf(-1f, 1f)) {
            for (z in listOf(-1f, 1f)) {
                CylinderNode(
                    radius = 0.025f,
                    height = -FloorY - TableThickness,
                    materialInstance = materials.woodDark,
                    position = Position(
                        x = x * (TableWidth / 2 - 0.06f),
                        y = (FloorY - TableThickness) / 2,
                        z = z * (TableDepth / 2 - 0.06f),
                    ),
                )
            }
        }
        // Props that give the scene a sense of scale and depth.
        CylinderNode(
            radius = 0.04f,
            height = 0.1f,
            materialInstance = materials.mug,
            position = Position(x = 0.42f, y = 0.05f, z = -0.2f),
        )
        CubeNode(
            size = Size(0.21f, 0.03f, 0.15f),
            materialInstance = materials.book,
            position = Position(x = -0.42f, y = 0.015f, z = -0.17f),
        )
        CubeNode(
            size = Size(0.2f, 0.022f, 0.14f),
            materialInstance = materials.pages,
            position = Position(x = -0.415f, y = 0.015f, z = -0.17f),
        )

        placedAt?.let { origin ->
            Node(position = origin) {
                SnackPlacements(
                    placements = placements,
                    instancesPerSnack = instancesPerSnack,
                    modelLoader = runtime.modelLoader,
                    donutStyle = donutStyle,
                    cameraPositionProvider = { cameraNode.worldPosition },
                )
            }
        }
    }
}

/** Where a screen ray meets the table top (y = 0), if it does. */
private fun tableHit(ray: dev.romainguy.kotlin.math.Ray?): Position? {
    ray ?: return null
    if (ray.direction.y >= 0f) return null
    val t = -ray.origin.y / ray.direction.y
    val x = ray.origin.x + ray.direction.x * t
    val z = ray.origin.z + ray.direction.z * t
    val margin = 0.08f
    if (x !in -TableWidth / 2 + margin..TableWidth / 2 - margin) return null
    if (z !in -TableDepth / 2 + margin..TableDepth / 2 - margin) return null
    return Position(x = x, y = 0f, z = z)
}

private class RoomMaterials(
    val wood: com.google.android.filament.MaterialInstance,
    val woodDark: com.google.android.filament.MaterialInstance,
    val floor: com.google.android.filament.MaterialInstance,
    val wall: com.google.android.filament.MaterialInstance,
    val mug: com.google.android.filament.MaterialInstance,
    val book: com.google.android.filament.MaterialInstance,
    val pages: com.google.android.filament.MaterialInstance,
)

private const val TableWidth = 1.3f
private const val TableDepth = 0.8f
private const val TableThickness = 0.04f
private const val FloorY = -0.75f
