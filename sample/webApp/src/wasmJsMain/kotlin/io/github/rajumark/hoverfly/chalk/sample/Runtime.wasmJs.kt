package io.github.rajumark.hoverfly.chalk.sample

actual val runtime: String = "Kotlin/Wasm"

actual fun setInk(g: org.w3c.dom.CanvasRenderingContext2D, color: String) { g.strokeStyle = color.toJsString() }
