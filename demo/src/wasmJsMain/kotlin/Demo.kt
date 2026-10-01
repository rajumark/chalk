@file:OptIn(ExperimentalWasmJsInterop::class)

import kotlin.js.ExperimentalWasmJsInterop
import io.github.rajumark.hoverfly.chalk.Chalk
import io.github.rajumark.hoverfly.chalk.Stroke

// Website live demo: docs/demo/worker.js calls load() once, then run() per input; run() returns JSON.

private fun q(s: String) = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\")
        else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

private var instance: Chalk? = null

private fun model(): Chalk = instance ?: Chalk().also { instance = it }

/** Loads the bundled model and warms it up. */
@JsExport
fun load() {
    model()
}

/** Guesses for a drawing, best first: [{label, score}]. [input] is "x,y,x,y,...;x,y,..." with one stroke per ';'. */
@JsExport
fun run(input: String, option: String): String {
    val strokes = input.split(';').filter { it.isNotBlank() }.map { s ->
        val v = s.split(',').map { it.toFloat() }
        Stroke(FloatArray(v.size / 2) { v[2 * it] }, FloatArray(v.size / 2) { v[2 * it + 1] })
    }
    return model().guess(strokes, 5).joinToString(",", "[", "]") { "{\"label\":${q(it.label)},\"score\":${it.score}}" }
}

fun main() {}
