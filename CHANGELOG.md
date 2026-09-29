# Changelog

## 2.0.0

- Kotlin Multiplatform: Android, JVM desktop, iOS (arm64 device + simulator), macOS arm64,
  JavaScript and WebAssembly, published to Maven Central as `io.github.rajumark:chalk`.
- New constructor `Chalk()`: the model ships inside the library on every platform, so no
  `Context` is needed. `Chalk(context)` still compiles on Android (deprecated).
- Same model and same results as 1.x; parity with the reference (150 drawings) is tested on every target.
- The sample is now a Compose Multiplatform app (Android, desktop, iOS) plus a web page (JS and Wasm).

## 1.0.0

- First version: `Chalk(context).guess(strokes)` returns the most likely of 345 everyday things (the QuickDraw
  categories) for a drawing given as pen strokes; `probabilities(strokes)`; `labels`.
- Works on half-finished drawings, so apps can guess while the user draws.
- Strokes in any coordinates (screen pixels): simplified on the device the same way the QuickDraw data was.
- Pure Kotlin inference with no dependencies, int8 weights (1.7 MB). minSdk 21.
- Hoverfly Community License: free up to 10,000 monthly active devices per product.
