package io.github.rajumark.hoverfly.chalk

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ChalkTest {
    private val chalk = ParityTest.testChalk()

    private fun circle(cx: Float, cy: Float, r: Float, n: Int = 60) =
        Stroke(FloatArray(n + 1) { cx + r * cos(2 * PI * it / n).toFloat() }, FloatArray(n + 1) { cy + r * sin(2 * PI * it / n).toFloat() })

    @Test
    fun knows345Labels() {
        assertEquals(345, chalk.labels.size)
        assertTrue("cat" in chalk.labels && "The Eiffel Tower" in chalk.labels)
    }

    @Test
    fun squareIsASquare() {
        val g = chalk.guess(listOf(Stroke.of(100f, 100f, 900f, 100f, 900f, 900f, 100f, 900f, 100f, 100f)), 5)
        println(g)
        assertTrue(g.any { it.label == "square" }, g.toString())
    }

    @Test
    fun smileyFace() {
        val face = listOf(circle(500f, 500f, 400f), circle(350f, 400f, 30f, 12), circle(650f, 400f, 30f, 12),
            Stroke.of(300f, 620f, 400f, 700f, 500f, 730f, 600f, 700f, 700f, 620f))
        val g = chalk.guess(face, 5)
        println(g)
        assertTrue(g.any { it.label == "smiley face" || it.label == "face" }, g.toString())
    }

    @Test
    fun sizeAndPositionDoNotMatter() {
        val a = chalk.guess(listOf(circle(100f, 100f, 50f)), 1)
        val b = chalk.guess(listOf(circle(5000f, 3000f, 900f)), 1)
        assertEquals(a.map { it.label }, b.map { it.label })
    }

    @Test
    fun emptyGivesNothing() {
        assertTrue(chalk.guess(emptyList()).isEmpty())
        assertTrue(chalk.guess(listOf(Stroke(FloatArray(0), FloatArray(0)))).isEmpty())
    }

    @Test
    fun scoresAreSortedProbabilities() {
        val g = chalk.guess(listOf(circle(0f, 0f, 10f)), 10)
        assertEquals(10, g.size)
        assertTrue(g.zipWithNext().all { (x, y) -> x.score >= y.score })
        val p = chalk.probabilities(listOf(circle(0f, 0f, 10f)))!!
        assertTrue(abs(p.sum() - 1f) < 1e-3f)
    }

    @Test
    fun closedInstanceThrows() {
        val c = Chalk()
        c.close()
        assertFailsWith<IllegalStateException> { c.guess(listOf(circle(0f, 0f, 1f))) }
    }

    @Test
    fun latency() {
        val drawings = ParityTest.vectors().map { it.raw }.filter { it.isNotEmpty() }
        repeat(100) { chalk.guess(drawings[it % drawings.size]) }
        val n = 300
        val t0 = TimeSource.Monotonic.markNow()
        repeat(n) { chalk.guess(drawings[it % drawings.size]) }
        val ms = t0.elapsedNow().inWholeMicroseconds / 1000.0 / n
        println("latency: ${(ms * 1000).roundToInt() / 1000.0} ms per drawing")
        val l0 = TimeSource.Monotonic.markNow()
        Chalk().close()
        println("load: ${l0.elapsedNow().inWholeMilliseconds} ms")
        assertTrue(ms < 2000.0, "too slow: $ms ms") // generous: Kotlin/Native test binaries are unoptimized debug builds
    }
}
