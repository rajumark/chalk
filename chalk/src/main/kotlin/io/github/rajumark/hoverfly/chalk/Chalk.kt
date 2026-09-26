package io.github.rajumark.hoverfly.chalk

import android.content.Context
import io.github.rajumark.hoverfly.chalk.internal.Network
import io.github.rajumark.hoverfly.chalk.internal.Strokes
import java.io.Closeable
import java.io.InputStream

/**
 * On-device doodle recogniser: reads the pen strokes of a drawing and guesses what it is, out of 345 everyday things
 * (the Google QuickDraw categories: cat, house, bicycle, pizza, the Eiffel Tower ...).
 *
 * ```
 * Chalk(context).use { chalk ->
 *     val guesses = chalk.guess(strokes)       // strokes from your drawing view, in screen pixels
 *     // a house: [Guess(label=house, score=0.89), Guess(label=barn, score=0.06), Guess(label=church, score=0.01)]
 * }
 * ```
 *
 * Works on half-finished drawings too, so you can guess while the user is still drawing.
 *
 * Everything runs locally: the model ships inside the library, there is no network, no permission and no
 * dependency. Creating an instance reads the model (tens of ms), so create it off the main thread and keep it
 * around; [guess] takes a few milliseconds and is safe to call from several threads.
 */
public class Chalk internal constructor(open: (String) -> InputStream) : Closeable {

    /** Loads the model bundled in the library's assets. */
    public constructor(context: Context) : this({ name -> context.assets.open("$ASSET_DIR/$name") })

    private var network: Network? = open("chalk.bin").use { Network(it) }

    /** The 345 things Chalk knows, in English, as QuickDraw names them (e.g. "cat", "hot air balloon", "The Eiffel Tower"). */
    public val labels: List<String> = open("labels.txt").bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }

    init {
        // The first calls run interpreted; pay that here (off the UI thread) instead of on the first drawing.
        val square = listOf(Stroke(floatArrayOf(0f, 100f, 100f, 0f, 0f), floatArrayOf(0f, 0f, 100f, 100f, 0f)))
        repeat(WARM_UP) { guess(square) }
    }

    /**
     * The [count] most likely things the drawing shows, best first. [strokes] are in any coordinate system (screen
     * pixels are fine): the drawing is re-scaled and simplified first, so its size and position do not matter.
     * Returns an empty list when there are no points.
     */
    @JvmOverloads
    public fun guess(strokes: List<Stroke>, count: Int = 3): List<Guess> {
        val p = probabilities(strokes) ?: return emptyList()
        val order = p.indices.sortedByDescending { p[it] }
        return order.take(count.coerceIn(0, p.size)).map { Guess(labels[it], p[it]) }
    }

    /** The probability of every label (same order as [labels]), or null when there are no points. */
    public fun probabilities(strokes: List<Stroke>): FloatArray? {
        val net = checkNotNull(network) { "Chalk is closed" }
        val feats = features(strokes)
        if (feats.isEmpty()) return null
        return net.probs(feats)
    }

    /** Releases the model. The instance cannot be used afterwards. */
    override fun close() {
        network = null
    }

    internal fun features(strokes: List<Stroke>): FloatArray =
        Strokes.prepare(strokes.map { s -> Strokes.Line(DoubleArray(s.x.size) { s.x[it].toDouble() }, DoubleArray(s.y.size) { s.y[it].toDouble() }) })

    internal companion object {
        const val ASSET_DIR = "chalk"
        const val WARM_UP = 20
    }
}

/**
 * One pen stroke: the points from finger-down to finger-up, as parallel x / y arrays (screen pixels are fine).
 * A tap is a stroke with one point.
 */
public class Stroke(public val x: FloatArray, public val y: FloatArray) {
    init {
        require(x.size == y.size) { "x and y must have the same length" }
    }

    public companion object {
        /** A stroke from (x0, y0, x1, y1, ...) pairs. */
        @JvmStatic
        public fun of(vararg xy: Float): Stroke {
            require(xy.size % 2 == 0) { "need x, y pairs" }
            return Stroke(FloatArray(xy.size / 2) { xy[2 * it] }, FloatArray(xy.size / 2) { xy[2 * it + 1] })
        }
    }
}

/** One guess: a label from [Chalk.labels] and its probability, 0..1. */
public data class Guess(val label: String, val score: Float)
