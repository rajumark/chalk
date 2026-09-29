package io.github.rajumark.hoverfly.chalk.internal

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The Chalk network in plain Kotlin, a sequence classifier over drawing points:
 *
 *   per point: Linear(6 features -> d) + position embedding -> LayerNorm -> N transformer layers (4 heads, post-LN)
 *   head:      mean over points -> LayerNorm -> Linear -> softmax over the categories
 *
 * Only real token positions are computed (the reference model masks padding with -1e4, whose softmax weight
 * underflows to exactly 0), so the result is the same. Stateless after loading: one instance can serve several threads.
 */
internal class Network(bin: ByteArray) {

    /** Row-wise symmetric int8 matrix: w[r][c] = scale[r] * q[r * cols + c]. */
    class Q8(val rows: Int, val cols: Int, val scale: FloatArray, val q: ByteArray) {
        /** Dequantized row-major copy, for the dense layers (float math is faster than int8 on ART). */
        fun dense(): Dense = Dense(rows, cols, FloatArray(rows * cols) { scale[it / cols] * q[it] })
    }

    class Dense(val rows: Int, val cols: Int, val w: FloatArray)

    private class Layer(
        val qkv: Dense, val qkvB: FloatArray,
        val out: Dense, val outB: FloatArray,
        val ln1: Pair<FloatArray, FloatArray>,
        val ff1: Dense, val ff1B: FloatArray,
        val ff2: Dense, val ff2B: FloatArray,
        val ln2: Pair<FloatArray, FloatArray>,
    )

    private val inp: Dense
    private val inpB: FloatArray
    private val pos: Q8
    private val ln0: Pair<FloatArray, FloatArray>
    private val layers: List<Layer>
    private val lnF: Pair<FloatArray, FloatArray>
    private val headW: Dense
    private val headB: FloatArray
    private val d: Int
    private val dHead: Int

    val maxPoints: Int get() = pos.rows

    init {
        val t = read(bin)
        fun q(n: String) = t[n] as? Q8 ?: error("chalk.bin: missing matrix $n")
        fun f(n: String) = t[n] as? FloatArray ?: error("chalk.bin: missing vector $n")
        fun ln(n: String) = f("$n.weight") to f("$n.bias")
        inp = q("inp.weight").dense(); inpB = f("inp.bias")
        pos = q("pos.weight")
        ln0 = ln("ln0")
        layers = generateSequence(0) { it + 1 }.takeWhile { t.containsKey("blocks.$it.qkv.weight") }.map { i ->
            val p = "blocks.$i"
            Layer(
                q("$p.qkv.weight").dense(), f("$p.qkv.bias"),
                q("$p.out.weight").dense(), f("$p.out.bias"),
                ln("$p.ln1"),
                q("$p.ff.0.weight").dense(), f("$p.ff.0.bias"),
                q("$p.ff.2.weight").dense(), f("$p.ff.2.bias"),
                ln("$p.ln2"),
            )
        }.toList()
        check(layers.isNotEmpty()) { "chalk.bin: no transformer layers" }
        lnF = ln("ln_f")
        headW = q("head.weight").dense(); headB = f("head.bias")
        d = inp.rows
        dHead = d / HEADS
    }

    /** Number of categories. */
    val nClasses: Int get() = headW.rows

    /** Category probabilities for one drawing; [feats] is row-major [n points x 6]. */
    fun probs(feats: FloatArray): FloatArray {
        val n = feats.size / Strokes.N_FEAT
        require(n in 1..maxPoints) { "need 1..$maxPoints points, got $n" }
        val x = FloatArray(n * d)
        val tmp = FloatArray(d)
        for (i in 0 until n) {
            linear(inp, inpB, feats, i * Strokes.N_FEAT, tmp, 0)
            for (c in 0 until d) x[i * d + c] = tmp[c]
            addRow(pos, i, x, i * d)
            layerNorm(x, i * d, d, ln0)
        }
        val qkv = FloatArray(n * 3 * d)
        val att = FloatArray(n * d)
        val s = FloatArray(n)
        val inv = 1f / sqrt(dHead.toFloat())
        val tmpAll = FloatArray(n * d)
        val hidAll = FloatArray(n * layers[0].ff1.rows)
        for (l in layers) {
            linearAll(l.qkv, l.qkvB, x, d, n, qkv, 3 * d)
            att.fill(0f)
            for (h in 0 until HEADS) {
                val ho = h * dHead
                for (i in 0 until n) {
                    val qo = i * 3 * d + ho
                    for (j in 0 until n) {
                        s[j] = dot(qkv, qo, qkv, j * 3 * d + d + ho, dHead) * inv
                    }
                    softmax(s, n)
                    val ao = i * d + ho
                    for (j in 0 until n) {
                        val w = s[j]
                        val vo = j * 3 * d + 2 * d + ho
                        for (c in 0 until dHead) att[ao + c] += w * qkv[vo + c]
                    }
                }
            }
            linearAll(l.out, l.outB, att, d, n, tmpAll, d)
            for (i in 0 until n) {
                for (c in 0 until d) x[i * d + c] += tmpAll[i * d + c]
                layerNorm(x, i * d, d, l.ln1)
            }
            val dff = l.ff1.rows
            linearAll(l.ff1, l.ff1B, x, d, n, hidAll, dff)
            for (k in 0 until n * dff) hidAll[k] = gelu(hidAll[k])
            linearAll(l.ff2, l.ff2B, hidAll, dff, n, tmpAll, d)
            for (i in 0 until n) {
                for (c in 0 until d) x[i * d + c] += tmpAll[i * d + c]
                layerNorm(x, i * d, d, l.ln2)
            }
        }
        val pooled = FloatArray(d)
        for (i in 0 until n) for (c in 0 until d) pooled[c] += x[i * d + c]
        for (c in 0 until d) pooled[c] /= n.toFloat()
        layerNorm(pooled, 0, d, lnF)
        val out = FloatArray(headW.rows)
        linear(headW, headB, pooled, 0, out, 0)
        softmax(out, out.size)
        return out
    }

    companion object {
        private const val HEADS = 4
        private const val EPS = 1e-5f

        /**
         * The same linear layer for all n rows of x (row stride xs), into out (row stride os). Weight rows are the
         * outer loop, so each row stays in the CPU cache while it meets every point; each sum is exactly the one
         * [linear] computes, so results are bit-identical.
         */
        private fun linearAll(m: Dense, b: FloatArray, x: FloatArray, xs: Int, n: Int, out: FloatArray, os: Int) {
            val cols = m.cols
            for (r in 0 until m.rows) {
                val wo = r * cols
                val br = b[r]
                for (i in 0 until n) out[i * os + r] = br + dot(m.w, wo, x, i * xs, cols)
            }
        }

        /** out[oo + r] = b[r] + sum_c W[r][c] * x[xo + c] */
        private fun linear(m: Dense, b: FloatArray, x: FloatArray, xo: Int, out: FloatArray, oo: Int) {
            for (r in 0 until m.rows) out[oo + r] = b[r] + dot(m.w, r * m.cols, x, xo, m.cols)
        }

        private fun linearNoBias(m: Dense, x: FloatArray, xo: Int, out: FloatArray, oo: Int) {
            for (r in 0 until m.rows) out[oo + r] = dot(m.w, r * m.cols, x, xo, m.cols)
        }

        /** sum_c a[ao + c] * b[bo + c], with 4 independent accumulators (ART does not vectorise; this is ~2x). */
        private fun dot(a: FloatArray, ao: Int, b: FloatArray, bo: Int, n: Int): Float {
            var s0 = 0f; var s1 = 0f; var s2 = 0f; var s3 = 0f
            var i = 0
            val n4 = n - 3
            while (i < n4) {
                s0 += a[ao + i] * b[bo + i]
                s1 += a[ao + i + 1] * b[bo + i + 1]
                s2 += a[ao + i + 2] * b[bo + i + 2]
                s3 += a[ao + i + 3] * b[bo + i + 3]
                i += 4
            }
            while (i < n) { s0 += a[ao + i] * b[bo + i]; i++ }
            return (s0 + s1) + (s2 + s3)
        }

        /** out[oo..] += row r of the matrix (an embedding lookup). */
        private fun addRow(m: Q8, r: Int, out: FloatArray, oo: Int) {
            val sc = m.scale[r]
            val base = r * m.cols
            for (c in 0 until m.cols) out[oo + c] += sc * m.q[base + c]
        }

        private fun layerNorm(v: FloatArray, o: Int, n: Int, p: Pair<FloatArray, FloatArray>) {
            var mean = 0f
            for (c in 0 until n) mean += v[o + c]
            mean /= n
            var varc = 0f
            for (c in 0 until n) { val t = v[o + c] - mean; varc += t * t }
            val inv = 1f / sqrt(varc / n + EPS)
            val (g, b) = p
            for (c in 0 until n) v[o + c] = (v[o + c] - mean) * inv * g[c] + b[c]
        }

        private fun softmax(v: FloatArray, n: Int) {
            var mx = Float.NEGATIVE_INFINITY
            for (i in 0 until n) if (v[i] > mx) mx = v[i]
            var sum = 0f
            for (i in 0 until n) { v[i] = exp(v[i] - mx); sum += v[i] }
            for (i in 0 until n) v[i] /= sum
        }

        /** Exact GELU, 0.5 x (1 + erf(x / sqrt 2)), as torch.nn.GELU(). */
        private fun gelu(x: Float): Float = (0.5 * x * (1.0 + erf(x / 1.4142135623730951))).toFloat()

        /** erf via the Numerical Recipes erfc Chebyshev fit, fractional error < 1.2e-7. */
        private fun erf(z: Double): Double {
            val a = kotlin.math.abs(z)
            val t = 1.0 / (1.0 + 0.5 * a)
            val r = t * exp(-a * a - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
                t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
                t * (-0.82215223 + t * 0.17087277)))))))))
            return if (z >= 0) 1.0 - r else r - 1.0
        }

        /** Parses chalk.bin: "MOJI" magic, version 1, then named tensors (int8 matrices, fp32 vectors). */
        private fun read(bytes: ByteArray): Map<String, Any> {
            val buf = LittleEndianReader(bytes)
            require(buf.bytes(4).decodeToString() == "MOJI") { "not a chalk.bin file" }
            val version = buf.int
            require(version == 1) { "unsupported chalk.bin version $version" }
            val out = HashMap<String, Any>()
            repeat(buf.int) {
                val name = buf.bytes(buf.short.toInt() and 0xFFFF).decodeToString()
                val dtype = buf.byte.toInt()
                val dims = IntArray(buf.byte.toInt()) { buf.int }
                val size = dims.fold(1) { a, b -> a * b }
                out[name] = when (dtype) {
                    0 -> FloatArray(size) { buf.float }
                    1 -> {
                        val scale = FloatArray(dims[0]) { buf.float }
                        Q8(dims[0], dims[1], scale, buf.bytes(size))
                    }
                    else -> error("chalk.bin: unknown dtype $dtype for $name")
                }
            }
            return out
        }
    }

    /** Sequential little-endian reads over a byte array (java.nio.ByteBuffer is JVM-only). */
    private class LittleEndianReader(private val b: ByteArray) {
        private var pos = 0

        val byte: Byte get() = b[pos++]

        val int: Int get() = (b[pos++].toInt() and 0xFF) or ((b[pos++].toInt() and 0xFF) shl 8) or
            ((b[pos++].toInt() and 0xFF) shl 16) or ((b[pos++].toInt() and 0xFF) shl 24)

        val short: Short get() = ((b[pos++].toInt() and 0xFF) or ((b[pos++].toInt() and 0xFF) shl 8)).toShort()

        val float: Float get() = Float.fromBits(int)

        fun bytes(n: Int): ByteArray = b.copyOfRange(pos, pos + n).also { pos += n }
    }
}
