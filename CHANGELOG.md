# Changelog

## 1.0.0

- First version: `Chalk(context).guess(strokes)` returns the most likely of 345 everyday things (the QuickDraw
  categories) for a drawing given as pen strokes; `probabilities(strokes)`; `labels`.
- Works on half-finished drawings, so apps can guess while the user draws.
- Strokes in any coordinates (screen pixels): simplified on the device the same way the QuickDraw data was.
- Pure Kotlin inference with no dependencies, int8 weights (1.7 MB). minSdk 21.
- Hoverfly Community License: free up to 10,000 monthly active devices per product.
