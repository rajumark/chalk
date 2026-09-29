package io.github.rajumark.hoverfly.chalk

import io.github.rajumark.hoverfly.chalk.internal.Strokes
import io.github.rajumark.hoverfly.chalk.internal.TestData
import io.github.rajumark.hoverfly.chalk.internal.decodeChunks
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the Kotlin port against the reference implementation on testvectors.tsv: the same simplified points from
 * screen-like float input, the same top-3 categories, and probabilities within 0.002. Runs on every target.
 */
class ParityTest {
    class Vector(val raw: List<Stroke>, val simplified: String, val top: List<Int>, val probs: List<Float>)

    @Test
    fun simplifyMatchesReference() {
        var bad = 0
        for (v in vectors()) {
            val lines = v.raw.map { s -> Strokes.Line(DoubleArray(s.x.size) { s.x[it].toDouble() }, DoubleArray(s.y.size) { s.y[it].toDouble() }) }
            val got = encode(Strokes.fit(Strokes.simplify(lines)))
            if (got != v.simplified) {
                bad++
                val k = got.indices.firstOrNull { it >= v.simplified.length || got[it] != v.simplified[it] } ?: got.length
                println("MISMATCH at $k\n  got  ${got.substring(maxOf(0, k - 80), minOf(got.length, k + 80))}\n  want ${v.simplified.substring(maxOf(0, k - 80), minOf(v.simplified.length, k + 80))}\n  strokes ${v.raw.size} lens ${got.split(';').map { it.split(' ').size }} vs ${v.simplified.split(';').map { it.split(' ').size }}")
            }
        }
        println("simplify: ${vectors().size - bad}/${vectors().size} identical")
        assertEquals(0, bad)
    }

    @Test
    fun modelMatchesReference() {
        val chalk = testChalk()
        var same = 0
        var maxDiff = 0f
        for (v in vectors()) {
            val p = chalk.probabilities(v.raw)!!
            val top = p.indices.sortedByDescending { p[it] }.take(3)
            for ((i, k) in v.top.withIndex()) maxDiff = maxOf(maxDiff, abs(p[k] - v.probs[i]))
            if (top == v.top) same++ else println("TOP DIFF: got $top ${top.map { p[it] }} want ${v.top} ${v.probs}")
        }
        println("model: top-3 order $same/${vectors().size}, max prob diff $maxDiff")
        assertTrue(same >= vectors().size - 2, "top-3 differ too often: $same/${vectors().size}") // near-ties may swap
        assertTrue(maxDiff < 0.002f, "probabilities differ by $maxDiff")
    }

    companion object {
        private var shared: Chalk? = null

        /** One instance per test run: loading is the slow part on the native and web targets. */
        fun testChalk(): Chalk = shared ?: Chalk().also { shared = it }

        internal fun encode(strokes: List<Strokes.Line>): String = strokes.joinToString(";") { s ->
            (0 until s.size).joinToString(" ") { "${s.x[it].toInt()},${s.y[it].toInt()}" }
        }

        fun parseStrokes(s: String): List<Stroke> = if (s.isEmpty()) emptyList() else s.split(';').map { st ->
            val pts = st.split(' ').map { p -> p.split(',').let { it[0].toFloat() to it[1].toFloat() } }
            Stroke(FloatArray(pts.size) { pts[it].first }, FloatArray(pts.size) { pts[it].second })
        }

        fun parse(lines: List<String>): List<Vector> = lines.map { it.split('\t') }.map { c ->
            Vector(parseStrokes(c[0]), c[1], c[2].split(',').map { it.toInt() }, c[3].split(',').map { it.toFloat() })
        }

        private var cached: List<Vector>? = null
        fun vectors(): List<Vector> = cached ?: parse(
            decodeChunks(TestData.files.getValue("testvectors.tsv")).decodeToString().lines().filter { it.isNotEmpty() }
        ).also { cached = it }
    }
}
