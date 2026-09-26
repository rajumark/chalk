package io.github.rajumark.hoverfly.chalk.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.chalk.Chalk
import io.github.rajumark.hoverfly.chalk.Guess
import io.github.rajumark.hoverfly.chalk.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ChalkTheme { ChalkScreen() } }
    }
}

/** Result of one inference, with its wall-clock time. */
private class Result(val guesses: List<Guess>, val micros: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChalkScreen() {
    val context = LocalContext.current.applicationContext

    // Loading reads the model: do it once, off the main thread.
    val chalk by produceState<Chalk?>(null) {
        value = withContext(Dispatchers.Default) { Chalk(context) }
        awaitDispose { value?.close() }
    }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // re-guess on every finished stroke and every ~8 new points of the current one (guessing while drawing)
    var version by remember { mutableStateOf(0) }

    val result by produceState<Result?>(null, chalk, version) {
        val c = chalk ?: return@produceState
        val all = strokes.toList() + listOfNotNull(current.takeIf { it.isNotEmpty() })
        value = withContext(Dispatchers.Default) {
            val input = all.map { pts -> Stroke(FloatArray(pts.size) { pts[it].x }, FloatArray(pts.size) { pts[it].y }) }
            val t0 = System.nanoTime()
            val g = c.guess(input, 3)
            Result(g, (System.nanoTime() - t0) / 1000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Chalk") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val ink = MaterialTheme.colorScheme.onSurface
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
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
                Canvas(Modifier.fillMaxSize()) {
                    val style = DrawStroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    for (s in strokes + listOf(current)) {
                        if (s.isEmpty()) continue
                        if (s.size == 1) {
                            drawCircle(ink, radius = 3.dp.toPx(), center = s[0])
                            continue
                        }
                        val path = Path().apply {
                            moveTo(s[0].x, s[0].y)
                            for (p in s.drop(1)) lineTo(p.x, p.y)
                        }
                        drawPath(path, ink, style = style)
                    }
                }
                if (strokes.isEmpty() && current.isEmpty()) {
                    Text("Draw something here", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(16.dp),
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
                                Text("%.0f%%".format(g.score * 100), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                            }
                        }
                        Text("${r.micros} µs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
fun ChalkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
