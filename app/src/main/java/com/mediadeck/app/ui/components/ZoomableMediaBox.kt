package com.mediadeck.app.ui.components

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.absoluteValue

@Composable
fun ZoomableMediaBox(
    modifier: Modifier = Modifier,
    maxScale: Float = 5f,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onScaleChange: ((Float) -> Unit)? = null,
    onOverSwipeHorizontal: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
    content: @Composable BoxScope.(scale: Float, offset: Offset) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnOverSwipeHorizontal by rememberUpdatedState(onOverSwipeHorizontal)
    val currentOnDragStopped by rememberUpdatedState(onDragStopped)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val width = maxWidth.value * LocalDensity.current.density
        val height = maxHeight.value * LocalDensity.current.density

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        awaitFirstDown(requireUnconsumed = false)
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
                                    val panMotion = pan.getDistance()

                                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, maxScale)
                                    val maxX = (width * (newScale - 1) / 2f).coerceAtLeast(0f)
                                    val maxY = (height * (newScale - 1) / 2f).coerceAtLeast(0f)

                                    val targetOffsetX = (offset.x + panChange.x).coerceIn(-maxX, maxX)
                                    val targetOffsetY = (offset.y + panChange.y).coerceIn(-maxY, maxY)

                                    val isAtHorizontalEdge = targetOffsetX == offset.x && panChange.x != 0f
                                    
                                    if (isAtHorizontalEdge && currentOnOverSwipeHorizontal != null && newScale <= 1.05f) {
                                    } else if (isAtHorizontalEdge && currentOnOverSwipeHorizontal != null) {
                                        currentOnOverSwipeHorizontal?.invoke(-panChange.x)
                                        event.changes.forEach { it.consume() }
                                    } else if (newScale != scale || targetOffsetX != offset.x || targetOffsetY != offset.y) {
                                        event.changes.forEach { it.consume() }
                                    }

                                    scale = newScale
                                    offset = Offset(targetOffsetX, targetOffsetY)
                                    currentOnScaleChange?.invoke(scale)
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })
                        
                        currentOnDragStopped?.invoke()
                    }
                }
                .pointerInput(scale) {
                    detectTapGestures(
                        onTap = { currentOnTap?.invoke() },
                        onDoubleTap = {
                            if (currentOnDoubleTap != null) {
                                currentOnDoubleTap?.invoke()
                            } else {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 3f
                                }
                                currentOnScaleChange?.invoke(scale)
                            }
                        }
                    )
                }
        ) {
            content(scale, offset)
        }
    }
}
