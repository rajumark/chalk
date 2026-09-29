package io.github.rajumark.hoverfly.chalk.sample

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ready-made drawings in 0..1 coordinates, so every platform can show the same guess without a finger. */
object Examples {
    private fun circle(cx: Float, cy: Float, r: Float, n: Int = 40) =
        List(n + 1) { cx + r * cos(2 * PI * it / n).toFloat() to cy + r * sin(2 * PI * it / n).toFloat() }

    private fun line(vararg xy: Float) = List(xy.size / 2) { xy[2 * it] to xy[2 * it + 1] }

    val all: List<Pair<String, List<List<Pair<Float, Float>>>>> = listOf(
        "House" to listOf(
            line(0.2f, 0.5f, 0.8f, 0.5f, 0.8f, 0.9f, 0.2f, 0.9f, 0.2f, 0.5f),
            line(0.14f, 0.52f, 0.5f, 0.14f, 0.86f, 0.52f),
            line(0.44f, 0.9f, 0.44f, 0.7f, 0.56f, 0.7f, 0.56f, 0.9f),
        ),
        "Smiley" to listOf(
            circle(0.5f, 0.5f, 0.4f),
            circle(0.36f, 0.4f, 0.03f, 10),
            circle(0.64f, 0.4f, 0.03f, 10),
            line(0.3f, 0.6f, 0.4f, 0.7f, 0.5f, 0.73f, 0.6f, 0.7f, 0.7f, 0.6f),
        ),
        "Sun" to listOf(circle(0.5f, 0.5f, 0.2f)) + List(8) {
            val a = 2 * PI * it / 8
            line((0.5 + 0.27 * cos(a)).toFloat(), (0.5 + 0.27 * sin(a)).toFloat(), (0.5 + 0.42 * cos(a)).toFloat(), (0.5 + 0.42 * sin(a)).toFloat())
        },
    )
}
