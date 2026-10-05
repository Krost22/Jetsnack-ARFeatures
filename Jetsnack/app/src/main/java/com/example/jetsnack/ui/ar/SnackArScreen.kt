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

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jetsnack.R
import com.example.jetsnack.model.DonutStyle
import com.example.jetsnack.model.DonutStyleRepo
import com.example.jetsnack.model.Snack
import com.example.jetsnack.model.SnackArModel
import com.example.jetsnack.model.SnackRepo
import com.example.jetsnack.model.SnackbarManager
import com.example.jetsnack.ui.components.JetsnackDivider
import com.example.jetsnack.ui.components.JetsnackSurface
import com.example.jetsnack.ui.components.QuantitySelector
import com.example.jetsnack.ui.home.cart.CartViewModel
import com.example.jetsnack.ui.theme.JetsnackTheme
import com.example.jetsnack.ui.theme.Neutral8
import com.example.jetsnack.ui.utils.formatPrice
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import io.github.sceneview.SceneScope
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.isTrackingPlane
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberOnGestureListener
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the AR screen puts on the table. */
sealed interface ArSubject {
    /** One snack, optionally next to a second one to compare their sizes. */
    data class SingleSnack(val snackId: Long) : ArSubject

    /** Everything in the cart that has a 3D model, as many copies as ordered. */
    data object Cart : ArSubject
}

@Composable
fun SnackArScreen(
    subject: ArSubject,
    upPress: () -> Unit,
    cartViewModel: CartViewModel = viewModel(factory = CartViewModel.provideFactory()),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val orderLines by cartViewModel.orderLines.collectAsStateWithLifecycle()
    val donutStyle = DonutStyleRepo.style
    var compareWithId by rememberSaveable { mutableStateOf<Long?>(null) }

    val mainSnack = (subject as? ArSubject.SingleSnack)?.let { remember(it.snackId) { SnackRepo.getSnack(it.snackId) } }
    val groups: List<Pair<Snack, Int>> = when (subject) {
        is ArSubject.SingleSnack -> listOfNotNull(
            mainSnack?.let { it to 1 },
            compareWithId?.let { SnackRepo.getSnack(it) to 1 },
        )

        ArSubject.Cart -> orderLines.filter { it.snack.arModel != null }.map { it.snack to it.count }
    }
    val placements = remember(groups) { layoutOnTable(groups, showLabels = subject is ArSubject.SingleSnack) }

    val simulated = remember { isRunningOnEmulator() }
    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var simulatedOrigin by remember { mutableStateOf<Position?>(null) }
    val placed = anchor != null || simulatedOrigin != null
    var anchorPlane by remember { mutableStateOf<Plane?>(null) }
    var trackingPlane by remember { mutableStateOf(false) }
    var showMoveHint by remember { mutableStateOf(false) }
    LaunchedEffect(anchor, simulatedOrigin) {
        if (placed) {
            showMoveHint = true
            delay(MoveHintMillis)
            showMoveHint = false
        }
    }

    // Cart updates can fail (the sample backend fails every few requests); surface those here too.
    val messages by SnackbarManager.messages.collectAsStateWithLifecycle()
    LaunchedEffect(messages) {
        messages.firstOrNull()?.let {
            Toast.makeText(context, it.messageId, Toast.LENGTH_SHORT).show()
            SnackbarManager.setMessageShown(it.id)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (simulated) {
            SimulatedTableScene(
                placements = placements,
                instancesPerSnack = if (subject == ArSubject.Cart) MaxCopiesPerSnack else 1,
                donutStyle = donutStyle,
                placedAt = simulatedOrigin,
                onPlaced = { simulatedOrigin = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SnackArScene(
                placements = placements,
                // The cart can change quantities live, so load the maximum copies once and only show
                // as many as ordered instead of reloading the model on every +/-.
                instancesPerSnack = if (subject == ArSubject.Cart) MaxCopiesPerSnack else 1,
                donutStyle = donutStyle,
                anchor = anchor,
                anchorPlane = anchorPlane,
                onPlaced = { newAnchor, plane ->
                    anchor?.let { runCatching { it.detach() } }
                    anchor = newAnchor
                    anchorPlane = plane
                },
                onTrackingPlane = { trackingPlane = true },
            )
        }

        val hint = when {
            !simulated && !trackingPlane && !placed -> R.string.ar_hint_find_surface
            !placed -> R.string.ar_hint_tap_to_place
            showMoveHint -> R.string.ar_hint_move
            else -> null
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            ArTopBar(
                title = when (subject) {
                    is ArSubject.SingleSnack -> mainSnack?.name.orEmpty()
                    ArSubject.Cart -> stringResource(R.string.ar_cart_title)
                },
                upPress = upPress,
                shareEnabled = placed,
                onShare = {
                    val frame = captureArFrame(view.rootView)
                    if (frame == null) {
                        Toast.makeText(context, R.string.ar_share_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch { shareArSnapshot(context, frame) }
                    }
                },
            )
            AnimatedContent(
                targetState = hint,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "hint",
            ) { hintRes ->
                if (hintRes != null) HintPill(stringResource(hintRes))
            }
        }

        ArBottomPanel(modifier = Modifier.align(Alignment.BottomCenter)) {
            when (subject) {
                is ArSubject.SingleSnack -> if (mainSnack != null) {
                    SingleSnackPanel(
                        snack = mainSnack,
                        donutStyle = donutStyle,
                        compareWithId = compareWithId,
                        onCompareWith = { compareWithId = it },
                    )
                }

                ArSubject.Cart -> CartPanel(
                    lines = orderLines.map { it.snack to it.count },
                    increase = cartViewModel::increaseSnackCount,
                    decrease = cartViewModel::decreaseSnackCount,
                )
            }
        }
    }
}

/** The camera feed with the snacks anchored to the table the user tapped. */
@Composable
private fun SnackArScene(
    placements: List<Placement>,
    instancesPerSnack: Int,
    donutStyle: DonutStyle,
    anchor: Anchor?,
    anchorPlane: Plane?,
    onPlaced: (Anchor, Plane) -> Unit,
    onTrackingPlane: () -> Unit,
) {
    val runtime = rememberSnackSceneRuntime()
    val engine = runtime.engine
    val modelLoader = runtime.modelLoader
    val materialLoader = runtime.materialLoader
    val cameraNode = rememberARCameraNode(engine)
    val cameraStream = rememberARCameraStream(materialLoader)
    // The latest ARCore frame, kept outside of Compose state so it does not recompose every frame.
    val latestFrame = remember { arrayOfNulls<Frame>(1) }

    ARSceneView(
        modifier = Modifier.fillMaxSize(),
        // A TextureView (instead of a SurfaceView) lets us grab the frame for sharing.
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
        // Depth lets real objects in front of the snacks hide them. Devices without depth support
        // silently fall back to no occlusion.
        depthMode = Config.DepthMode.AUTOMATIC,
        planeRenderer = anchor == null,
        cameraStream = cameraStream,
        cameraNode = cameraNode,
        onSessionResumed = { session ->
            cameraStream.isDepthOcclusionEnabled = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        },
        onSessionUpdated = { _, frame ->
            latestFrame[0] = frame
            if (frame.isTrackingPlane()) onTrackingPlane()
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { event, _ ->
                val hit = latestFrame[0]?.hitTest(event.x, event.y)?.firstOrNull { hit ->
                    val plane = hit.trackable as? Plane
                    plane != null &&
                        plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                        plane.isPoseInPolygon(hit.hitPose)
                }
                if (hit != null) {
                    runCatching { hit.createAnchor() }.getOrNull()?.let { onPlaced(it, hit.trackable as Plane) }
                }
            },
        ),
    ) {
        anchorPlane?.let { ShadowReceiverPlane(plane = it) }
        anchor?.let { placedAnchor ->
            AnchorNode(anchor = placedAnchor) {
                SnackPlacements(
                    placements = placements,
                    instancesPerSnack = instancesPerSnack,
                    modelLoader = modelLoader,
                    donutStyle = donutStyle,
                    cameraPositionProvider = { cameraNode.worldPosition },
                )
            }
        }
    }
}

/**
 * The snacks laid out around the current origin: one parse per snack, every copy an instance
 * sharing its GPU buffers. Shared by the real AR view and the emulator's simulated table.
 */
@Composable
internal fun SceneScope.SnackPlacements(
    placements: List<Placement>,
    instancesPerSnack: Int,
    modelLoader: ModelLoader,
    donutStyle: DonutStyle,
    cameraPositionProvider: () -> Position,
) {
    placements.groupBy { it.snackId }.forEach { (snackId, copies) ->
        key(snackId) {
            val model = copies.first().model
            // One parse per snack; every copy is an instance sharing its GPU buffers.
            val instances = rememberInstancedModels(modelLoader, model.assetPath, instancesPerSnack)
            copies.forEach { placement ->
                val instance = instances?.getOrNull(placement.copy) ?: return@forEach
                key(placement.copy) {
                    SnackModelNode(
                        instance = instance,
                        customizable = model.customizable,
                        donutStyle = donutStyle,
                        position = placement.position,
                        rotation = Rotation(y = placement.yawDegrees),
                    )
                    if (placement.label != null) {
                        TextNode(
                            text = placement.label,
                            widthMeters = 0.14f,
                            heightMeters = 0.035f,
                            position = Position(
                                x = placement.position.x,
                                y = model.heightMeters + 0.04f,
                                z = placement.position.z,
                            ),
                            cameraPositionProvider = cameraPositionProvider,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArTopBar(title: String, upPress: () -> Unit, shareEnabled: Boolean, onShare: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CircleIconButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.label_back),
            onClick = upPress,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontStyle = FontStyle.Italic,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        CircleIconButton(
            icon = R.drawable.ic_photo_camera,
            contentDescription = stringResource(R.string.ar_share),
            onClick = onShare,
            enabled = shareEnabled,
        )
    }
}

@Composable
private fun CircleIconButton(icon: Int, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .background(color = Neutral8.copy(alpha = 0.32f), shape = CircleShape),
    ) {
        Icon(
            painter = painterResource(id = icon),
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            contentDescription = contentDescription,
        )
    }
}

@Composable
private fun HintPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        modifier = Modifier
            .padding(top = 8.dp)
            .background(Neutral8.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ArBottomPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    JetsnackSurface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ColumnScope.SingleSnackPanel(snack: Snack, donutStyle: DonutStyle, compareWithId: Long?, onCompareWith: (Long?) -> Unit) {
    val model = snack.arModel
    if (model?.customizable == true) {
        DonutCustomizer(style = donutStyle, onStyleChange = { DonutStyleRepo.style = it })
        Spacer(Modifier.height(16.dp))
        JetsnackDivider()
        Spacer(Modifier.height(12.dp))
    }
    Text(
        text = stringResource(R.string.ar_compare_with),
        style = MaterialTheme.typography.labelSmall,
        color = JetsnackTheme.colors.textHelp,
    )
    Spacer(Modifier.height(8.dp))
    val others = remember(snack.id) { SnackRepo.getArSnacks().filter { it.id != snack.id } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        item {
            SelectableChip(stringResource(R.string.ar_compare_none), compareWithId == null) { onCompareWith(null) }
        }
        items(others, key = { it.id }) { other ->
            SelectableChip(other.name, compareWithId == other.id) { onCompareWith(other.id) }
        }
    }
}

@Composable
private fun ColumnScope.CartPanel(lines: List<Pair<Snack, Int>>, increase: (Long) -> Unit, decrease: (Long) -> Unit) {
    val modeled = lines.filter { it.first.arModel != null }
    if (modeled.isEmpty()) {
        Text(
            text = stringResource(R.string.ar_cart_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = JetsnackTheme.colors.textHelp,
        )
    }
    modeled.forEach { (snack, count) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = snack.name,
                style = MaterialTheme.typography.titleMedium,
                color = JetsnackTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            QuantitySelector(
                count = count,
                decreaseItemCount = { decrease(snack.id) },
                increaseItemCount = { increase(snack.id) },
            )
        }
    }
    if (modeled.size < lines.size) {
        Text(
            text = stringResource(R.string.ar_cart_no_models),
            style = MaterialTheme.typography.bodyMedium,
            color = JetsnackTheme.colors.textHelp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    JetsnackDivider()
    Text(
        text = stringResource(R.string.ar_cart_total, formatPrice(lines.sumOf { (snack, count) -> snack.price * count })),
        style = MaterialTheme.typography.titleMedium,
        color = JetsnackTheme.colors.textPrimary,
        modifier = Modifier
            .align(Alignment.End)
            .padding(top = 8.dp),
    )
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    JetsnackSurface(
        shape = CircleShape,
        color = if (selected) JetsnackTheme.colors.brand else JetsnackTheme.colors.uiBackground,
        contentColor = if (selected) JetsnackTheme.colors.textInteractive else JetsnackTheme.colors.textSecondary,
        border = BorderStroke(1.dp, JetsnackTheme.colors.brand),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** One model instance on the table, relative to the anchor the user tapped. */
internal data class Placement(
    val snackId: Long,
    val copy: Int,
    val model: SnackArModel,
    val position: Position,
    val yawDegrees: Float,
    val label: String?,
)

/**
 * Lays snacks out in a centered grid on the table, as many copies as requested (capped so a large
 * order stays readable). Copies get a slightly different yaw so they look naturally placed.
 */
internal fun layoutOnTable(groups: List<Pair<Snack, Int>>, showLabels: Boolean): List<Placement> {
    val items = groups.flatMap { (snack, count) -> List(count.coerceIn(0, MaxCopiesPerSnack)) { copy -> snack to copy } }
    if (items.isEmpty()) return emptyList()
    val columns = ceil(sqrt(items.size.toDouble())).toInt()
    val rows = ceil(items.size / columns.toDouble()).toInt()
    val cell = items.maxOf { (snack, _) -> snack.arModel!!.footprintMeters } + CellGapMeters
    return items.mapIndexed { index, (snack, copy) ->
        val model = snack.arModel!!
        Placement(
            snackId = snack.id,
            copy = copy,
            model = model,
            position = Position(
                x = (index % columns - (columns - 1) / 2f) * cell,
                y = 0f,
                z = (index / columns - (rows - 1) / 2f) * cell,
            ),
            yawDegrees = ((index * 47) % 40 - 20).toFloat(),
            label = if (showLabels && copy == 0) "${snack.name} · ${(model.footprintMeters * 100).roundToInt()} cm" else null,
        )
    }
}

private const val MaxCopiesPerSnack = 6
private const val CellGapMeters = 0.03f
private const val MoveHintMillis = 4_000L
