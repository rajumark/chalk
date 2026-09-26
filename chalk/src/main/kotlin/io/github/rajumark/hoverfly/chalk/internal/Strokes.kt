package io.github.rajumark.hoverfly.chalk.internal

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Strokes -> model input. Must behave exactly like the reference implementation (chalk/strokes.py); ParityTest checks
 * the simplified points and the features on every test vector. All geometry is in Double, like Python.
 *
 *   simplify: align to the top-left, scale the longer side to 255, resample every 1 unit, RDP epsilon 2, round
 *   fit:      at most MAX_POINTS points (coarser RDP with epsilon 3, 4, 5 ..., then cut)
 *   features: per point x/255, y/255, dx/255, dy/255, stroke start, stroke end
 */
internal object Strokes {
    const val MAX_POINTS = 128
    const val N_FEAT = 6
    private const val EPS = 2.0

    /** A stroke as parallel coordinate arrays. */
    class Line(val x: DoubleArray, val y: DoubleArray) {
        val size: Int get() = x.size
    }

    private fun round(v: Double): Int = floor(v + 0.5).toInt()

    /** Ramer-Douglas-Peucker; distance to the infinite line through the ends (to the first point if they coincide). */
    fun rdp(s: Line, eps: Double): Line {
        val n = s.size
        if (n < 3) return s
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val ax = s.x[a]; val ay = s.y[a]
            val dx = s.x[b] - ax; val dy = s.y[b] - ay
            val norm = hypot(dx, dy)
            var best = -1.0
            var idx = -1
            for (i in a + 1 until b) {
                val d = if (norm == 0.0) hypot(s.x[i] - ax, s.y[i] - ay)
                else abs(dy * (s.x[i] - ax) - dx * (s.y[i] - ay)) / norm
                if (d > best) { best = d; idx = i }
            }
            if (best > eps) {
                keep[idx] = true
                stack.addLast(intArrayOf(a, idx))
                stack.addLast(intArrayOf(idx, b))
            }
        }
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        for (i in 0 until n) if (keep[i]) { xs.add(s.x[i]); ys.add(s.y[i]) }
        return Line(xs.toDoubleArray(), ys.toDoubleArray())
    }

    /** Points every [step] units along the polyline; the first and last points are always kept. */
    fun resample(s: Line, step: Double = 1.0): Line {
        if (s.size < 2) return s
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        xs.add(s.x[0]); ys.add(s.y[0])
        var carry = 0.0
        for (i in 0 until s.size - 1) {
            val x0 = s.x[i]; val y0 = s.y[i]; val x1 = s.x[i + 1]; val y1 = s.y[i + 1]
            val seg = hypot(x1 - x0, y1 - y0)
            if (seg == 0.0) continue
            var t = step - carry
            while (t <= seg) {
                xs.add(x0 + (x1 - x0) * t / seg); ys.add(y0 + (y1 - y0) * t / seg)
                t += step
            }
            carry = seg - (t - step)
        }
        val lx = s.x[s.size - 1]; val ly = s.y[s.size - 1]
        if (xs.last() != lx || ys.last() != ly) { xs.add(lx); ys.add(ly) }
        return Line(xs.toDoubleArray(), ys.toDoubleArray())
    }

    /** Raw strokes (any scale) -> integer strokes in 0..255, QuickDraw-style. */
    fun simplify(raw: List<Line>): List<Line> {
        val strokes = raw.filter { it.size > 0 }
        if (strokes.isEmpty()) return emptyList()
        var mx = Double.POSITIVE_INFINITY; var my = Double.POSITIVE_INFINITY
        var Mx = Double.NEGATIVE_INFINITY; var My = Double.NEGATIVE_INFINITY
        for (s in strokes) for (i in 0 until s.size) {
            if (s.x[i] < mx) mx = s.x[i]; if (s.x[i] > Mx) Mx = s.x[i]
            if (s.y[i] < my) my = s.y[i]; if (s.y[i] > My) My = s.y[i]
        }
        val span = maxOf(Mx - mx, My - my)
        val scale = if (span > 0) 255.0 / span else 1.0
        return strokes.map { s ->
            val t = Line(DoubleArray(s.size) { (s.x[it] - mx) * scale }, DoubleArray(s.size) { (s.y[it] - my) * scale })
            val r = rdp(resample(t), EPS)
            Line(DoubleArray(r.size) { round(r.x[it]).coerceIn(0, 255).toDouble() },
                DoubleArray(r.size) { round(r.y[it]).coerceIn(0, 255).toDouble() })
        }
    }

    /** At most [maxPoints] points in total (coarser RDP, then cut). */
    fun fit(input: List<Line>, maxPoints: Int = MAX_POINTS): List<Line> {
        var strokes = input
        var eps = EPS + 1.0
        while (strokes.sumOf { it.size } > maxPoints && eps <= 20.0) {
            val e = eps
            strokes = strokes.map { rdp(it, e) }
            eps += 1.0
        }
        val out = ArrayList<Line>()
        var n = 0
        for (s in strokes) {
            if (n >= maxPoints) break
            val k = minOf(s.size, maxPoints - n)
            out.add(if (k == s.size) s else Line(s.x.copyOf(k), s.y.copyOf(k)))
            n += k
        }
        return out
    }

    /** Integer strokes -> row-major features [n points x N_FEAT]. */
    fun features(strokes: List<Line>): FloatArray {
        val n = strokes.sumOf { it.size }
        val out = FloatArray(n * N_FEAT)
        var k = 0
        var px = Double.NaN; var py = Double.NaN
        for (s in strokes) for (i in 0 until s.size) {
            val x = s.x[i]; val y = s.y[i]
            val o = k * N_FEAT
            out[o] = (x / 255.0).toFloat()
            out[o + 1] = (y / 255.0).toFloat()
            out[o + 2] = if (px.isNaN()) 0f else ((x - px) / 255.0).toFloat()
            out[o + 3] = if (py.isNaN()) 0f else ((y - py) / 255.0).toFloat()
            out[o + 4] = if (i == 0) 1f else 0f
            out[o + 5] = if (i == s.size - 1) 1f else 0f
            px = x; py = y
            k++
        }
        return out
    }

    fun prepare(raw: List<Line>): FloatArray = features(fit(simplify(raw)))
}
