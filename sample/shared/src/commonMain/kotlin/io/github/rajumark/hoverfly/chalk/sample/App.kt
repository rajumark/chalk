package io.github.rajumark.hoverfly.chalk.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.chalk.Chalk
import io.github.rajumark.hoverfly.chalk.Guess
import io.github.rajumark.hoverfly.chalk.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** Result of one inference, with its wall-clock time. */
private class Result(val guesses: List<Guess>, val micros: Long)

/** The whole demo: draw (or pick an example), see what Chalk guesses. [platform] is shown so screenshots say where they ran. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(platform: String) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            // Loading reads the model: do it once, off the main thread.
            val chalk by produceState<Chalk?>(null) { value = withContext(Dispatchers.Default) { Chalk() } }
            // Strokes in canvas pixels; the example drawings are scaled to the canvas size.
            val strokes = remember { mutableStateListOf<List<Offset>>() }
            var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
            var version by remember { mutableStateOf(0) }
            var canvasPx by remember { mutableStateOf(0f) }
            var pending by remember { mutableStateOf<String?>("House") }

            val result by produceState<Result?>(null, chalk, version) {
                val c = chalk ?: return@produceState
                val all = strokes.toList() + listOfNotNull(current.takeIf { it.isNotEmpty() })
                value = withContext(Dispatchers.Default) {
                    val input = all.map { pts -> Stroke(FloatArray(pts.size) { pts[it].x }, FloatArray(pts.size) { pts[it].y }) }
                    val t0 = TimeSource.Monotonic.markNow()
                    val g = c.guess(input, 3)
                    Result(g, t0.elapsedNow().inWholeMicroseconds)
                }
            }

            fun load(name: String) {
                val ex = Examples.all.first { it.first == name }.second
                strokes.clear()
                current = emptyList()
                strokes.addAll(ex.map { s -> s.map { (x, y) -> Offset(x * canvasPx, y * canvasPx) } })
                version++
            }

            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Chalk", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Kotlin Multiplatform · $platform · io.github.rajumark:chalk:$CHALK_VERSION",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ink = MaterialTheme.colorScheme.onSurface
                val density = LocalDensity.current
                val drawPad: @Composable (Modifier) -> Unit = { modifier ->
                    BoxWithConstraints(
                        modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var pts = listOf(down.position)
                                    current = pts
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        if (ch.positionChange() != Offset.Zero) {
                                            pts = pts + ch.position
                                            current = pts
                                            if (pts.size % 8 == 0) version++
                                            ch.consume()
                                        }
                                    }
                                    strokes.add(pts)
                                    current = emptyList()
                                    version++
                                }
                            },
                    ) {
                        canvasPx = with(density) { maxWidth.toPx() }
                        pending?.let { if (canvasPx > 0f) { pending = null; load(it) } }
                        Canvas(Modifier.fillMaxSize()) {
                            val style = DrawStroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            for (s in strokes + listOf(current)) {
                                if (s.isEmpty()) continue
                                if (s.size == 1) { drawCircle(ink, radius = 3.dp.toPx(), center = s[0]); continue }
                                val path = Path().apply { moveTo(s[0].x, s[0].y); for (p in s.drop(1)) lineTo(p.x, p.y) }
                                drawPath(path, ink, style = style)
                            }
                        }
                        if (strokes.isEmpty() && current.isEmpty()) {
                            Text("Draw something here", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val guesses: @Composable (Modifier) -> Unit = { modifier ->
                    Column(
                        modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Chalk thinks it's", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        val r = result
                        when {
                            chalk == null -> Box(Modifier.fillMaxWidth().height(48.dp), Alignment.Center) { CircularProgressIndicator() }
                            r == null || r.guesses.isEmpty() -> Text("…", style = MaterialTheme.typography.titleLarge)
                            else -> {
                                r.guesses.forEachIndexed { i, g ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            g.label,
                                            style = if (i == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Box(Modifier.width(96.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                            Box(Modifier.fillMaxHeight().fillMaxWidth(g.score.coerceIn(0.02f, 1f)).background(MaterialTheme.colorScheme.primary))
                                        }
                                        Text("${(g.score * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                                    }
                                }
                                Text("${r.micros / 1000.0} ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Side by side on wide windows (desktop, web, tablets), stacked on phones.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 700.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            drawPad(Modifier.width(360.dp))
                            guesses(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            drawPad(Modifier.widthIn(max = 480.dp).fillMaxWidth())
                            guesses(Modifier.widthIn(max = 480.dp).fillMaxWidth())
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((name, _) in Examples.all) SuggestionChip(onClick = { load(name) }, label = { Text(name) })
                    OutlinedButton(onClick = { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); version++ } },
                        enabled = strokes.isNotEmpty()) { Text("Undo") }
                    Button(onClick = { strokes.clear(); current = emptyList(); version++ }, enabled = strokes.isNotEmpty()) { Text("Clear") }
                }
                Text(
                    "345 things · runs on this device · no network, no permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

const val CHALK_VERSION = "2.0.0"
