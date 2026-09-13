# How the output is checked

Why a file OmniPlotter writes is one the calculator will open, and how that is tested rather than
assumed. [← Back to the README](../README.md)

## Correctness

These containers carry the same length in several places plus checksums derived from it. One wrong
byte and the calculator refuses the file, with no indication of why. So the conversion is checked
against the original rather than reasoned about:

`tools/refgen/img2calc.mjs` contains the img2calc encoders transcribed from its `index.html` as
literally as possible, and `refgen.mjs` generates **591 golden vectors** — every format, across a range of sizes
(including deliberately awkward ones: 1×1, odd widths, sizes that are not multiples of 8) and pixel
patterns. `ReferenceVectorTest` feeds the Java encoders the identical pixels and asserts byte
equality.

**All 591 match.** Regenerate them with `./omniplotter.sh refgen` after touching the reference.

That covers the encoders, which turn pixels into a file. The stage before it — resizing, colour
reduction, palette remapping — is ImageMagick in the reference and reimplemented in Java here, so it
cannot match bit for bit. `PreprocessingParityTest` runs a real ImageMagick with the reference's
exact arguments and compares: same canvas, same colour count, and pixels close enough that the
picture is visibly the same. It skips itself if ImageMagick is not installed.

A few of those operators do not do the obvious thing, and the code says so where it matters:
`-depth 5` rescales to 32 evenly spread levels rather than masking off low bits, `-extent` defaults
to opaque white, `-channel` keeps applying to later operators, and `-posterize` and `-colors` dither
unless `+dither` was passed.

## Faithfully odd

Some behaviour is faithfully odd because the reference is: the TI checksum sums untruncated values
while the file stores truncated bytes, `im8c` drops literal pixels still buffered when the image
ends, and `zpic` wraps on the canvas width rather than the image width. These are reproduced
deliberately — the goal is files that behave exactly like img2calc's.
