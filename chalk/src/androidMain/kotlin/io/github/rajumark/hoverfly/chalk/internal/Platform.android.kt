package io.github.rajumark.hoverfly.chalk.internal

import io.github.rajumark.hoverfly.chalk.Chalk

// The model ships as Java resources in the jar/AAR (src/modelData), so no Context or copy is needed.
internal actual fun readModelFile(name: String): ByteArray =
    Chalk::class.java.getResourceAsStream("/io/github/rajumark/hoverfly/chalk/model/$name")?.use { it.readBytes() }
        ?: error("Chalk model file $name is missing from the library jar")
