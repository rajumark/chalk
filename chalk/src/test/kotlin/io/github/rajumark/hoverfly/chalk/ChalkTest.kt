package io.github.rajumark.hoverfly.chalk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
        assertTrue(g.toString(), g.any { it.label == "square" })
    }

    @Test
    fun smileyFace() {
        val face = listOf(circle(500f, 500f, 400f), circle(350f, 400f, 30f, 12), circle(650f, 400f, 30f, 12),
            Stroke.of(300f, 620f, 400f, 700f, 500f, 730f, 600f, 700f, 700f, 620f))
        val g = chalk.guess(face, 5)
        println(g)
        assertTrue(g.toString(), g.any { it.label == "smiley face" || it.label == "face" })
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
        assertEquals(1f, p.sum(), 1e-3f)
    }

    @Test(expected = IllegalStateException::class)
    fun closedInstanceThrows() {
        val c = ParityTest.testChalk()
        c.close()
        c.guess(listOf(circle(0f, 0f, 1f)))
    }

    @Test
    fun latency() {
        val drawings = ParityTest.vectors().map { it.raw }.filter { it.isNotEmpty() }
        repeat(300) { chalk.guess(drawings[it % drawings.size]) }
        val n = 2000
        val t0 = System.nanoTime()
        repeat(n) { chalk.guess(drawings[it % drawings.size]) }
        val ms = (System.nanoTime() - t0) / 1e6 / n
        println("JVM latency: %.3f ms per drawing".format(ms))
        assertTrue("too slow: $ms ms", ms < 20.0)
    }
}
