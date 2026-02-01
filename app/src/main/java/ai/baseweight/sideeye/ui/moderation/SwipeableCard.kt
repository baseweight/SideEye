package ai.baseweight.sideeye.ui.moderation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

enum class SwipeDirection {
    LEFT,   // Delete
    RIGHT,  // Keep
    UP      // Vault
}

@Composable
fun SwipeableCard(
    modifier: Modifier = Modifier,
    onSwipe: (SwipeDirection) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val swipeThreshold = screenWidth * 0.35f
    val upSwipeThreshold = screenHeight * 0.25f

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }

    // Sync animated values with drag values
    LaunchedEffect(offsetX, offsetY) {
        animatedOffsetX.snapTo(offsetX)
        animatedOffsetY.snapTo(offsetY)
    }

    // Calculate rotation based on horizontal offset
    val rotation = (animatedOffsetX.value / screenWidth) * 15f

    // Calculate overlay colors based on swipe direction
    val leftOverlayAlpha = ((-animatedOffsetX.value) / swipeThreshold).coerceIn(0f, 0.5f)
    val rightOverlayAlpha = (animatedOffsetX.value / swipeThreshold).coerceIn(0f, 0.5f)
    val upOverlayAlpha = ((-animatedOffsetY.value) / upSwipeThreshold).coerceIn(0f, 0.5f)

    Box(
        modifier = modifier
            .offset { IntOffset(animatedOffsetX.value.roundToInt(), animatedOffsetY.value.roundToInt()) }
            .rotate(rotation)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offsetX < -swipeThreshold -> {
                                    // Animate off screen left
                                    animatedOffsetX.animateTo(
                                        targetValue = -screenWidth * 1.5f,
                                        animationSpec = tween(300)
                                    )
                                    onSwipe(SwipeDirection.LEFT)
                                }
                                offsetX > swipeThreshold -> {
                                    // Animate off screen right
                                    animatedOffsetX.animateTo(
                                        targetValue = screenWidth * 1.5f,
                                        animationSpec = tween(300)
                                    )
                                    onSwipe(SwipeDirection.RIGHT)
                                }
                                offsetY < -upSwipeThreshold -> {
                                    // Animate off screen up
                                    animatedOffsetY.animateTo(
                                        targetValue = -screenHeight * 1.5f,
                                        animationSpec = tween(300)
                                    )
                                    onSwipe(SwipeDirection.UP)
                                }
                                else -> {
                                    // Snap back to center
                                    launch { animatedOffsetX.animateTo(0f, tween(300)) }
                                    launch { animatedOffsetY.animateTo(0f, tween(300)) }
                                }
                            }
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        // Only allow upward drag for vault
                        if (dragAmount.y < 0 || offsetY < 0) {
                            offsetY += dragAmount.y
                            offsetY = offsetY.coerceAtMost(0f) // Only up
                        }
                    }
                )
            }
    ) {
        // Card content
        content()

        // Left swipe overlay (Delete - Red)
        if (leftOverlayAlpha > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = leftOverlayAlpha)),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = leftOverlayAlpha * 2),
                    modifier = Modifier.padding(start = 32.dp)
                )
            }
        }

        // Right swipe overlay (Keep - Green)
        if (rightOverlayAlpha > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Green.copy(alpha = rightOverlayAlpha)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Keep",
                    tint = Color.White.copy(alpha = rightOverlayAlpha * 2),
                    modifier = Modifier.padding(end = 32.dp)
                )
            }
        }

        // Up swipe overlay (Vault - Blue)
        if (upOverlayAlpha > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = upOverlayAlpha)),
                contentAlignment = Alignment.TopCenter
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Move to Vault",
                    tint = Color.White.copy(alpha = upOverlayAlpha * 2),
                    modifier = Modifier.padding(top = 32.dp)
                )
            }
        }
    }
}
