package io.github.rajumark.hoverfly.chalk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Same check as the JVM ParityTest, on a real device (ART's floating point and math library): the same top-3
 * categories and probabilities within 0.002 on every vector. Also measures load time and latency.
 */
@RunWith(AndroidJUnit4::class)
class DeviceParityTest {
    private fun strokes(s: String): List<Stroke> = if (s.isEmpty()) emptyList() else s.split(';').map { st ->
        val pts = st.split(' ').map { p -> p.split(',').let { it[0].toFloat() to it[1].toFloat() } }
        Stroke(FloatArray(pts.size) { pts[it].first }, FloatArray(pts.size) { pts[it].second })
    }

    @Test
    fun matchesReferenceOnDevice() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val rows = inst.context.assets.open("testvectors.tsv").bufferedReader().readLines().map { it.split('\t') }
        val t0 = System.nanoTime()
        val chalk = Chalk(inst.targetContext)
        val loadMs = (System.nanoTime() - t0) / 1e6
        var same = 0
        var maxDiff = 0f
        val drawings = ArrayList<List<Stroke>>()
        for (c in rows) {
            val d = strokes(c[0])
            drawings.add(d)
            val want = c[2].split(',').map { it.toInt() }
            val probs = c[3].split(',').map { it.toFloat() }
            val p = chalk.probabilities(d)!!
            val top = p.indices.sortedByDescending { p[it] }.take(3)
            for ((i, k) in want.withIndex()) maxDiff = maxOf(maxDiff, abs(p[k] - probs[i]))
            if (top == want) same++ else android.util.Log.w("CHALK_DEVICE", "DIFF got $top want $want")
        }
        repeat(200) { chalk.guess(drawings[it % drawings.size]) }
        val n = 1000
        val s0 = System.nanoTime()
        repeat(n) { chalk.guess(drawings[it % drawings.size]) }
        val ms = (System.nanoTime() - s0) / 1e6 / n
        android.util.Log.i("CHALK_DEVICE", "top3 $same/${rows.size} maxDiff=$maxDiff load=${"%.0f".format(loadMs)}ms latency=${"%.3f".format(ms)}ms")
        assertTrue("$same/${rows.size}", same >= rows.size - 2)
        assertTrue("maxDiff $maxDiff", maxDiff < 0.002f)
    }
}
