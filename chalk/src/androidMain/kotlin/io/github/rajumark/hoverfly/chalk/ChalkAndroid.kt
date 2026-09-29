@file:JvmName("ChalkAndroid")

package io.github.rajumark.hoverfly.chalk

import android.content.Context

/** Kept so 1.x code (`Chalk(context)`) still compiles; the model no longer needs a [Context]. */
@Deprecated("The model is bundled without assets now; use Chalk().", ReplaceWith("Chalk()"))
@Suppress("UNUSED_PARAMETER", "FunctionName")
public fun Chalk(context: Context): Chalk = Chalk()
