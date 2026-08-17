# CasioPicture

A desktop app that converts images into the picture and script formats graphing calculators can
open — Casio `.g3p` / `.g4p` / `.c2p`, TI `.8xv` / `.8ca` / `.8ci` / `.8xi` and friends, the Zero
`pic`, and the Python generators for kandinsky, casioplot, gint, ti_draw, nsp and hpprime.

It is a native port of [TI-Planet's img2calc](https://tiplanet.org/forum/img2calc.php), with both a
window and a command line.

## Getting started

```bash
./setup.sh
```

Downloads a JDK and Maven into `tools/` and resolves every dependency into `.mvn/repo` and `libs/`.
Nothing is installed system-wide and your `~/.m2` is never touched, so the checkout is
self-contained and safe to delete.

```bash
./casiopicture.sh run          # open the app
./casiopicture.sh cli --help   # command line
./casiopicture.sh test         # test suite
./casiopicture.sh package      # native installer for this platform
```

## Command line

```bash
casiopicture convert photo.png -f cp.g3p --name PICT1
casiopicture convert *.jpg -f kandinsky.py -o ./out
casiopicture formats --target cg
casiopicture targets --mode var
casiopicture inspect PICT1.g3p
```

Format and target identifiers are the same strings img2calc uses in its URLs, so a link from the web
tool translates directly into a command here.

`inspect` reads a Casio file back apart — un-inverts the header, checks the sizes recorded in
different places against each other, undoes the CP obfuscation and inflates the pixel data. That is
the question that actually matters: whether the calculator will open the file.

## Correctness

These containers carry the same length in several places plus checksums derived from it. One wrong
byte and the calculator refuses the file, with no indication of why. So the conversion is checked
against the original rather than reasoned about:

`tools/refgen/refgen.mjs` contains the img2calc encoders transcribed from `tmp/index.html` as
literally as possible, and generates **591 golden vectors** — every format, across a range of sizes
(including deliberately awkward ones: 1×1, odd widths, sizes that are not multiples of 8) and pixel
patterns. `ReferenceVectorTest` feeds the Java encoders the identical pixels and asserts byte
equality.

**All 591 match.** Regenerate them with `./casiopicture.sh refgen` after touching the reference.

That covers the encoders, which turn pixels into a file. The stage before it — resizing, colour
reduction, palette remapping — is ImageMagick in the reference and reimplemented in Java here, so it
cannot match bit for bit. `PreprocessingParityTest` runs a real ImageMagick with the reference's
exact arguments and compares: same canvas, same colour count, and pixels close enough that the
picture is visibly the same. It skips itself if ImageMagick is not installed.

A few of those operators do not do the obvious thing, and the code says so where it matters:
`-depth 5` rescales to 32 evenly spread levels rather than masking off low bits, `-extent` defaults
to opaque white, `-channel` keeps applying to later operators, and `-posterize` and `-colors` dither
unless `+dither` was passed.

## Layout

```
src/main/java/com/github/casiopicture/
  Main.java              arguments mean CLI, none means UI
  cli/                   convert, formats, targets, inspect
  gui/                   JavaFX window
  engine/
    EngineApi            the one conversion path both faces use
    converter/           ImageOps (the ImageMagick operators), ImagePreprocessor (per-format pipeline)
    encoder/             CasioPicture, TIZ80, Python, Zero
    data/                Format, Target, FormatConfig, ConversionOptions
    inspect/             reads Casio files back apart
    util/                PixelBuffer, Palette, ByteSeq, EncoderUtils
tools/refgen/            the reference encoders and the golden-vector generator
tmp/                     img2calc itself, kept so the vectors can be regenerated
```

## Notes

- The preview in the window is the actual preprocessed image, not a scaled-down source, so
  quantisation and dithering are visible before anything is written.
- Some behaviour is faithfully odd because the reference is: the TI checksum sums untruncated values
  while the file stores truncated bytes, `im8c` drops literal pixels still buffered when the image
  ends, and `zpic` wraps on the canvas width rather than the image width. These are reproduced
  deliberately — the goal is files that behave exactly like img2calc's.

## Credits

img2calc is by Xavier Andréani (@critor) and Adrien Bertrand (@Adriweb).
