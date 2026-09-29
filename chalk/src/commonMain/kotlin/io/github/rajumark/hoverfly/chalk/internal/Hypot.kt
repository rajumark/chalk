package io.github.rajumark.hoverfly.chalk.internal

import kotlin.math.sqrt

/*
 * sqrt(x*x + y*y) without undue overflow, a port of fdlibm's e_hypot.c:
 *
 * ====================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * ====================================================
 *
 * kotlin.math.hypot is the platform's: fdlibm on the JVM, Android and Wasm, but the system libm on
 * Apple and Math.hypot on JS, which round differently in rare cases. Drawing simplification (RDP)
 * compares distances, so one ulp can keep a different point; this port gives every platform the
 * JVM's exact result.
 */
internal fun hypot(x: Double, y: Double): Double {
    var ha = hi(x) and 0x7fffffff
    var hb = hi(y) and 0x7fffffff
    var a: Double
    var b: Double
    if (hb > ha) { a = y; b = x; val j = ha; ha = hb; hb = j } else { a = x; b = y }
    a = withHi(a, ha) // |a|
    b = withHi(b, hb) // |b|
    if (ha - hb > 0x3c00000) return a + b // a/b > 2**60
    var k = 0
    if (ha > 0x5f300000) { // a > 2**500
        if (ha >= 0x7ff00000) { // Inf or NaN
            var w = a + b
            if (((ha and 0xfffff) or lo(a)) == 0) w = a
            if (((hb xor 0x7ff00000) or lo(b)) == 0) w = b
            return w
        }
        // scale a and b by 2**-600
        ha -= 0x25800000; hb -= 0x25800000; k += 600
        a = withHi(a, ha)
        b = withHi(b, hb)
    }
    if (hb < 0x20b00000) { // b < 2**-500
        if (hb <= 0x000fffff) { // subnormal b or 0
            if ((hb or lo(b)) == 0) return a
            val t1 = fromWords(0x7fd00000, 0) // 2**1022
            b *= t1
            a *= t1
            k -= 1022
        } else { // scale a and b by 2**600
            ha += 0x25800000
            hb += 0x25800000
            k -= 600
            a = withHi(a, ha)
            b = withHi(b, hb)
        }
    }
    // medium size a and b
    var w = a - b
    if (w > b) {
        val t1 = fromWords(ha, 0)
        val t2 = a - t1
        w = sqrt(t1 * t1 - (b * (-b) - t2 * (a + t1)))
    } else {
        a += a
        val y1 = fromWords(hb, 0)
        val y2 = b - y1
        val t1 = fromWords(ha + 0x00100000, 0)
        val t2 = a - t1
        w = sqrt(t1 * y1 - (w * (-w) - (t1 * y2 + t2 * b)))
    }
    return if (k != 0) fromWords(0x3ff00000 + (k shl 20), 0) * w else w
}

private fun hi(d: Double): Int = (d.toRawBits() ushr 32).toInt()

private fun lo(d: Double): Int = d.toRawBits().toInt()

private fun fromWords(hi: Int, lo: Int): Double = Double.fromBits((hi.toLong() shl 32) or (lo.toLong() and 0xFFFFFFFFL))

private fun withHi(d: Double, hi: Int): Double = fromWords(hi, lo(d))
