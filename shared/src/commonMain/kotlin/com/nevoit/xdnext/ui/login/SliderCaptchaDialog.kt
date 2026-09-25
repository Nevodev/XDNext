package com.nevoit.xdnext.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nevoit.material.core.component.CircularProgressIndicator
import com.nevoit.material.core.component.Surface
import com.nevoit.material.core.component.Text
import com.nevoit.material.core.component.TextButton
import com.nevoit.material.theme.MaterialTheme
import com.nevoit.xdnext.core.image.decodeImageBitmap
import com.nevoit.xdnext.core.platform.currentTimeMillis
import com.nevoit.xdnext.data.ids.CaptchaTrackPoint
import com.nevoit.xdnext.data.ids.SliderCaptchaChallenge
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lets a human solve the slider captcha, the fallback the original always shipped beside its solver.
 *
 * The drag is recorded the way the original recorded it: a point at the start, then further points only
 * every 20 ms and only after 2 px of movement. The server scores the *shape* of the movement, so a
 * synthesised straight line is rejected — which is why the fallback exists rather than simply posting
 * an answer.
 *
 * A rejected challenge is spent, so a failure fetches a fresh one. That happens through
 * [LoginViewModel.refreshCaptcha], which updates the flow this dialog is keyed on — so the reset comes
 * for free from the recomposition rather than from manual bookkeeping.
 *
 * Geometry note: the server's background is roughly 590x360 and its size varies per challenge, so the
 * on-screen size is derived from the decoded images. Everything is scaled by one factor, which keeps
 * the piece strip aligned with the background. The *canvas* stays a fixed
 * [SliderCaptchaChallenge.CANVAS_WIDTH] units wide because that is the space the answer is expressed
 * in, and drawing the background exactly that many dp wide makes one dp equal one canvas unit.
 */
@Composable
fun SliderCaptchaDialog(
    viewModel: LoginViewModel,
    challenge: SliderCaptchaChallenge,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    val puzzle = remember(challenge) { decodeImageBitmap(challenge.puzzleImage) }
    val piece = remember(challenge) { decodeImageBitmap(challenge.pieceImage) }

    val canvasWidth = SliderCaptchaChallenge.CANVAS_WIDTH

    // Fall back to the observed 590x360 aspect if a decode fails, so the layout stays sane.
    val puzzlePixelWidth = puzzle?.width?.takeIf { it > 0 } ?: 590
    val puzzlePixelHeight = puzzle?.height?.takeIf { it > 0 } ?: 360
    val piecePixelWidth = piece?.width?.takeIf { it > 0 } ?: 93
    val piecePixelHeight = piece?.height?.takeIf { it > 0 } ?: 360

    val scale = canvasWidth.toFloat() / puzzlePixelWidth
    val displayHeight = (puzzlePixelHeight * scale).dp
    val pieceDisplayWidth = (piecePixelWidth * scale).dp
    val pieceDisplayHeight = (piecePixelHeight * scale).dp

    val maxDrag = (canvasWidth - SLIDER_RIGHT_PADDING).toFloat()

    // All of this is keyed on the challenge so a refresh resets the interaction automatically.
    var dragX by remember(challenge) { mutableFloatStateOf(0f) }
    var dragY by remember(challenge) { mutableFloatStateOf(0f) }
    var dragActive by remember(challenge) { mutableStateOf(false) }
    var submitting by remember(challenge) { mutableStateOf(false) }
    var error by remember(challenge) { mutableStateOf<String?>(null) }

    val tracks = remember(challenge) { mutableListOf<CaptchaTrackPoint>() }
    var lastRecordAt by remember(challenge) { mutableStateOf(0L) }
    var lastX by remember(challenge) { mutableIntStateOf(Int.MIN_VALUE) }
    var lastY by remember(challenge) { mutableIntStateOf(Int.MIN_VALUE) }

    Dialog(onDismissRequest = { }) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colors.elevatedCardBackground,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("请拖动滑块完成拼图", style = MaterialTheme.type.headline)

                Box(
                    modifier = Modifier
                        .size(canvasWidth.dp, displayHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.inactiveTrack),
                ) {
                    if (puzzle != null) {
                        Image(
                            bitmap = puzzle,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (piece != null) {
                        Image(
                            bitmap = piece,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = dragX.dp)
                                .size(pieceDisplayWidth, pieceDisplayHeight),
                        )
                    }
                }

                // The finger track. Vertical movement is recorded too, because the server scores it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pointerInput(challenge) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    // Only a grab on the handle starts a drag, matching the original.
                                    val startCanvas = start.x / density
                                    dragActive = startCanvas >= dragX - HANDLE_GRAB_SLACK &&
                                            startCanvas <= dragX + canvasWidth * 0.2f
                                    if (dragActive) {
                                        error = null
                                        tracks.clear()
                                        tracks.add(CaptchaTrackPoint(0, 0, 0))
                                        lastRecordAt = currentTimeMillis()
                                        lastX = Int.MIN_VALUE
                                        lastY = Int.MIN_VALUE
                                    }
                                },
                                onDrag = { change, amount ->
                                    if (dragActive && !submitting) {
                                        change.consume()
                                        dragX = (dragX + amount.x / density).coerceIn(0f, maxDrag)
                                        dragY += amount.y / density

                                        val now = currentTimeMillis()
                                        val elapsed = (now - lastRecordAt).toInt()
                                        if (elapsed >= RECORD_INTERVAL_MS) {
                                            val x = dragX.roundToInt()
                                            val y = dragY.roundToInt()
                                            val moved = if (lastX == Int.MIN_VALUE) {
                                                true
                                            } else {
                                                val dx = x - lastX
                                                val dy = y - lastY
                                                dx * dx + dy * dy >=
                                                        RECORD_DISTANCE_PX * RECORD_DISTANCE_PX
                                            }
                                            if (moved) {
                                                tracks.add(CaptchaTrackPoint(x, y, elapsed))
                                                lastX = x
                                                lastY = y
                                                lastRecordAt = now
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragActive && !submitting && dragX > 0f) {
                                        dragActive = false
                                        tracks.add(
                                            CaptchaTrackPoint(
                                                x = dragX.roundToInt(),
                                                y = dragY.roundToInt(),
                                                millis = (currentTimeMillis() - lastRecordAt).toInt()
                                                    .coerceAtLeast(0),
                                            ),
                                        )
                                        val submission = tracks.toList()
                                        submitting = true
                                        scope.launch {
                                            if (viewModel.submitCaptcha(submission)) {
                                                // Success completes the broker; this dialog disappears.
                                            } else {
                                                error = "没通过，换一张再试"
                                                viewModel.refreshCaptcha()
                                            }
                                            submitting = false
                                        }
                                    } else {
                                        dragActive = false
                                    }
                                },
                                onDragCancel = { dragActive = false },
                            )
                        },
                ) {
                    // Track.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = 5.dp)
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colors.inactiveTrack),
                    )
                    // Filled portion.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(y = 5.dp)
                            .fillMaxWidth((dragX / canvasWidth).coerceIn(0f, 1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colors.activeTrack),
                    )
                    // Handle. Its container tone keeps it distinguishable from the filled track,
                    // which is the same reason the original used a container colour here.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = dragX.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.segmentedControlBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = "›",
                                style = MaterialTheme.type.title3,
                                color = MaterialTheme.colors.onSegmentedControlBackground,
                            )
                        }
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.type.footnote,
                        color = MaterialTheme.colors.error,
                    )
                }

                TextButton(
                    onClick = { viewModel.cancelCaptcha() },
                    enabled = !submitting,
                ) {
                    Text("跳过")
                }
            }
        }
    }
}

/** The original left 40 logical pixels of track unused at the right edge. */
private const val SLIDER_RIGHT_PADDING = 40

/** How far from the handle a press still counts as grabbing it, in logical pixels. */
private const val HANDLE_GRAB_SLACK = 12f

/** Minimum gap between recorded track points, matching the original's recorder. */
private const val RECORD_INTERVAL_MS = 20

/** Minimum movement before another track point is recorded, in logical pixels. */
private const val RECORD_DISTANCE_PX = 2
