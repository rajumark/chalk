package io.github.rajumark.hoverfly.chalk.internal

/** Bytes of a bundled model file: chalk.bin or labels.txt. */
internal expect fun readModelFile(name: String): ByteArray
