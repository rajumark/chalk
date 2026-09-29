package io.github.rajumark.hoverfly.chalk

import io.github.rajumark.hoverfly.chalk.internal.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common fdlibm port must give exactly StrictMath.hypot (what JVM and Android use), bit for bit. */
class HypotTest {
    @Test
    fun matchesStrictMath() {
        val rnd = Random(7)
        val special = listOf(0.0, -0.0, 1.0, -1.0, 1e-310, 1e300, 1e-300, Double.MAX_VALUE, Double.MIN_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, 3.0, 4.0, 255.0)
        for (a in special) for (b in special) check(a, b)
        repeat(2_000_000) {
            val a = when (it % 3) { 0 -> rnd.nextDouble(-300.0, 300.0); 1 -> rnd.nextDouble(-2.0, 2.0); else -> Double.fromBits(rnd.nextLong()) }
            val b = when (it % 4) { 0 -> rnd.nextDouble(-300.0, 300.0); 1 -> a * rnd.nextDouble(); 2 -> rnd.nextDouble(-1e-3, 1e-3); else -> Double.fromBits(rnd.nextLong()) }
            check(a, b)
        }
    }

    private fun check(a: Double, b: Double) {
        val want = StrictMath.hypot(a, b)
        val got = hypot(a, b)
        if (want.isNaN()) assertEquals(true, got.isNaN(), "hypot($a, $b)")
        else assertEquals(want.toRawBits(), got.toRawBits(), "hypot($a, $b): $got vs $want")
    }
}
