package com.mediadeck.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SnapScale = 1.05f
private const val DoubleTapScale = 3f

private fun IntSize.maxPan(scale: Float): Offset = Offset(
    (width * (scale - 1f) / 2f).coerceAtLeast(0f),
    (height * (scale - 1f) / 2f).coerceAtLeast(0f),
)

private fun Offset.clampTo(max: Offset): Offset =
    Offset(x.coerceIn(-max.x, max.x), y.coerceIn(-max.y, max.y))


@Composable
fun ZoomableMediaBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 5f,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onScaleChange: ((Float) -> Unit)? = null,
    onOverSwipeHorizontal: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
    enableTapGestures: Boolean = true,
    applyTransform: Boolean = false,
    content: @Composable BoxScope.(scale: Float, offset: Offset) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val currentMaxScale by rememberUpdatedState(maxScale)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnOverSwipeHorizontal by rememberUpdatedState(onOverSwipeHorizontal)
    val currentOnDragStopped by rememberUpdatedState(onDragStopped)

    fun update(newScale: Float, newOffset: Offset) {
        val scaleChanged = newScale != scale
        scale = newScale
        offset = newOffset
        if (scaleChanged) currentOnScaleChange?.invoke(newScale)
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        animJob?.cancel()
        val startScale = scale
        val startOffset = offset
        animJob = scope.launch {
            Animatable(0f).animateTo(1f, tween(250, easing = FastOutSlowInEasing)) {
                update(
                    lerp(startScale, targetScale, value),
                    lerpOffset(startOffset, targetOffset, value),
                )
            }
        }
    }

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        animJob?.cancel()
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    pan += panChange
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1 - zoom) * centroidSize
                                    val multiTouch = event.changes.count { it.pressed } > 1
                                    if (scale > SnapScale) {
                                        if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) {
                                            pastTouchSlop = true
                                        }
                                    } else {


                                        if (multiTouch && (zoomMotion > touchSlop || pan.getDistance() > touchSlop)) {
                                            pastTouchSlop = true
                                        }
                                    }
                                }
                                if (pastTouchSlop) {
                                    val prevScale = scale
                                    val newScale = (prevScale * zoomChange).coerceIn(1f, currentMaxScale)
                                    val effectiveZoom = newScale / prevScale


                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val c = if (centroid.isSpecified) centroid - center else Offset.Zero
                                    val raw = panChange + c - (c - offset) * effectiveZoom

                                    val clamped = raw.clampTo(size.maxPan(newScale))
                                    val overshootX = raw.x - clamped.x
                                    val multiTouch = event.changes.count { it.pressed } > 1
                                    val hasOverSwipe = currentOnOverSwipeHorizontal != null
                                    val pushingOut = overshootX != 0f && !multiTouch && hasOverSwipe
                                    val changed = newScale != prevScale || clamped != offset

                                    when {

                                        !multiTouch && newScale <= SnapScale -> Unit

                                        pushingOut -> {
                                            currentOnOverSwipeHorizontal?.invoke(-overshootX)
                                            event.changes.forEach { it.consume() }
                                        }
                                        changed || multiTouch -> event.changes.forEach { it.consume() }
                                    }
                                    update(newScale, clamped)
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })


                        if (scale < SnapScale && (scale != 1f || offset != Offset.Zero)) {
                            animateTo(1f, Offset.Zero)
                        }
                        currentOnDragStopped?.invoke()
                    }
                }
                .pointerInput(enableTapGestures) {
                    if (!enableTapGestures) return@pointerInput
                    detectTapGestures(
                        onTap = { currentOnTap?.invoke() },
                        onDoubleTap = { tap ->
                            val custom = currentOnDoubleTap
                            if (custom != null) {
                                custom()
                            } else if (scale > SnapScale) {
                                animateTo(1f, Offset.Zero)
                            } else {
                                val target = DoubleTapScale.coerceAtMost(currentMaxScale)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val c = tap - center
                                val raw = c - (c - offset) * (target / scale)
                                animateTo(target, raw.clampTo(size.maxPan(target)))
                            }
                        }
                    )
                }
                .then(
                    if (applyTransform) {
                        Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                    } else Modifier
                )
        ) {
            if (applyTransform) content(1f, Offset.Zero) else content(scale, offset)
        }
    }
}
