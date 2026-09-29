package io.github.rajumark.hoverfly.chalk.sample

import io.github.rajumark.hoverfly.chalk.Chalk
import io.github.rajumark.hoverfly.chalk.Stroke
import kotlinx.browser.document
import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasLineJoin
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.ROUND
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** "Kotlin/JS" or "Kotlin/Wasm". */
expect val runtime: String

/** CanvasRenderingContext2D.strokeStyle takes a dynamic value on JS and a JsAny on Wasm. */
expect fun setInk(g: CanvasRenderingContext2D, color: String)

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

fun main() {
    fun el(id: String) = document.getElementById(id) as HTMLElement
    el("platform").textContent = "Kotlin Multiplatform · $runtime · io.github.rajumark:chalk:2.0.0"

    val t0 = TimeSource.Monotonic.markNow()
    val chalk = Chalk()
    el("load").textContent = "Model loaded in ${t0.elapsedNow().inWholeMilliseconds} ms"

    val canvas = document.getElementById("pad") as HTMLCanvasElement
    val size = canvas.width.toDouble()
    val g = canvas.getContext("2d") as CanvasRenderingContext2D
    val strokes = ArrayList<ArrayList<Pair<Double, Double>>>()
    var drawing = false

    fun redraw() {
        g.clearRect(0.0, 0.0, size, size)
        g.lineWidth = size / 80
        g.lineCap = CanvasLineCap.ROUND
        g.lineJoin = CanvasLineJoin.ROUND
        setInk(g, "#1b1b21")
        for (s in strokes) {
            if (s.isEmpty()) continue
            g.beginPath()
            g.moveTo(s[0].first, s[0].second)
            for (p in s.drop(1)) g.lineTo(p.first, p.second)
            if (s.size == 1) g.lineTo(s[0].first + 0.1, s[0].second)
            g.stroke()
        }
    }

    fun guess() {
        redraw()
        val mark = TimeSource.Monotonic.markNow()
        val r = chalk.guess(strokes.map { s -> Stroke(FloatArray(s.size) { s[it].first.toFloat() }, FloatArray(s.size) { s[it].second.toFloat() }) }, 3)
        val ms = mark.elapsedNow().inWholeMicroseconds / 1000.0
        el("hint").style.display = if (strokes.isEmpty()) "block" else "none"
        el("bars").innerHTML = if (r.isEmpty()) "<div class=\"name\">…</div>" else r.mapIndexed { i, it ->
            "<div class=\"bar\"><span class=\"${if (i == 0) "top" else "lbl"}\">${esc(it.label)}</span><div class=\"track\"><div class=\"fill\" style=\"width:${(it.score * 100).roundToInt()}%\"></div></div>" +
                "<span class=\"pct\">${(it.score * 100).roundToInt()}%</span></div>"
        }.joinToString("")
        el("timing").textContent = if (r.isEmpty()) "" else "$ms ms"
    }

    fun pos(e: MouseEvent): Pair<Double, Double> {
        val rect = canvas.getBoundingClientRect()
        return (e.clientX - rect.left) * size / rect.width to (e.clientY - rect.top) * size / rect.height
    }
    canvas.onmousedown = { e -> drawing = true; strokes.add(arrayListOf(pos(e))); guess(); null }
    canvas.onmousemove = { e -> if (drawing) { strokes.last().add(pos(e)); if (strokes.last().size % 8 == 0) guess() else redraw() }; null }
    document.onmouseup = { if (drawing) { drawing = false; guess() }; null }

    fun load(name: String) {
        strokes.clear()
        for (s in Examples.all.first { it.first == name }.second) strokes.add(ArrayList(s.map { (x, y) -> x * size to y * size }))
        guess()
    }
    el("examples").innerHTML = Examples.all.joinToString("") { "<button class=\"chip\">${esc(it.first)}</button>" } +
        "<button class=\"chip\" id=\"undo\">Undo</button><button class=\"chip\" id=\"clear\">Clear</button>"
    val buttons = el("examples").querySelectorAll("button")
    for (i in 0 until Examples.all.size) {
        val b = buttons.item(i) as HTMLElement
        b.onclick = { load(b.textContent ?: ""); null }
    }
    el("undo").onclick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); guess(); null }
    el("clear").onclick = { strokes.clear(); guess(); null }
    load("House")
}
