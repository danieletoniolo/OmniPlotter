# OmniPlotter

A desktop application that puts your pictures on a graphing calculator. It converts images into the
picture and script formats calculators can open — Casio `.g3p` / `.g4p` / `.c2p`, TI `.8xv` /
`.8ca` / `.8ci` / `.8xi` and friends, the Zero `pic`, and the Python generators for kandinsky,
casioplot, gint, ti_draw, nsp and hpprime.

It also takes a PDF or a large scan and cuts it into pieces that each fill the calculator's screen,
which is what makes a page of notes readable on one.

There is a window and there is a command line, and they are the same program: the installed
application runs as either.

The file formats come from [TI-Planet's img2calc](https://tiplanet.org/forum/img2calc.php), and the
encoders are checked byte for byte against it — 591 golden vectors, every format, all matching. What
is built on top of them is this project's own: the window, PDFs cut into tiles, the photo and
document treatments, reading a calculator file back apart, and a command line for whole folders at
once.

## Installing

The [releases page](../../releases/latest) carries an installer for macOS, Windows and Linux, a
`SHA256SUMS` to check a download against, and `omniplotter-<version>-cli.jar` — the command line
without the window, small and platform-independent, for anyone who already has a JDK 21.

The builds are not signed, so the first launch is refused on both desktops. On macOS, open System
Settings → Privacy & Security after the refusal and choose "Open Anyway"; the right-click → Open
route is no longer reliable on recent versions. From a terminal,
`xattr -dr com.apple.quarantine /Applications/OmniPlotter.app` does the same. On Windows,
SmartScreen wants "More info" → "Run anyway".

## The window

Drop images or a PDF onto the window, or click the empty list to pick them. The panel on the right
asks its questions in the order a conversion happens — which calculator, how big, how it should
look, how to cut a page up, what the calculator should call the file — and only asks the ones that
apply to what you dropped.

The preview beside the source is not a scaled-down copy of it: it is the actual preprocessed image,
the same pixels the encoder will consume, so the dithering and the palette banding are visible
before anything is written. It says up front when a file will be too big for the calculator to
take, rather than letting you find out at transfer time.

The window is in English and Italian, and follows the desktop's light or dark setting.

## Command line

```bash
omniplotter convert photo.png -f cp.g3p --name PICT1
omniplotter convert *.jpg -f kandinsky.py -o ./out
omniplotter formats --target cg
omniplotter targets --mode var
omniplotter inspect PICT1.g3p
```

Format and target identifiers are the same strings img2calc uses in its URLs, so a link from the web
tool translates directly into a command here.

A photograph and a screenshot of text want opposite treatment, and the conversion is tuned for the
first. `--look document` turns dithering off and hardens the contrast, which is the difference
between legible small text and mush on a sixteen-colour screen. `--fill` crops the source to the
canvas proportions instead of padding it out with white.

```bash
omniplotter convert notes.png -f cp.g3p --look document
omniplotter convert portrait.jpg -f 8ca --fill --enlarge-smaller
```

Each control is also available on its own — `--dither`, `--brightness`, `--contrast`, `--gamma`,
`--saturation`, `--sharpen`, `--crop x,y,w,h` — and anything given alongside a `--look` overrides
it. Every one of them defaults to what img2calc does, so a command without them converts exactly as
before.

### Documents

A PDF converts like anything else, and a whole A4 page on a 384-pixel screen is about as readable as
a postage stamp. `--grid` cuts it into pieces that each fill the screen and converts every one:

```bash
omniplotter grids --target cg
omniplotter convert notes.pdf -f cp.g3p --look document --grid 8x3 -o ./tiles
```

`grids` exists because "how many parts?" is the wrong question. Cutting a page across adds no
resolution at all — three horizontal bands fit a 2:1 screen almost perfectly and leave the page at
46 dpi, exactly where it started — so what the table shows is the resolution each grid reaches and
how tall a line of type ends up at it:

```
GRID     TILES   DPI    10 PT TEXT
3x1      3       46     6 px per line
6x2      12      93     13 px per line
8x3      24      139    19 px per line
```

Whether a line that tall reads is not something a converter can decide: it depends on the document
as much as the screen. Pass `--text-size` for the type you actually have — the same three bands give
18-point notes twelve pixels a line, which is a different answer.

`--pages 1-3,7` picks pages, `--overlap` sets how far tiles reach into each other so a line of text
falling on a cut survives in one of them, and `--start-slot` says where the numbering begins on
calculators that address pictures by number. Tiles are named `notes-p2-r3c1.g3p` on disk and
numbered `NOTES14` on the calculator, because eight characters is all there is in the second place.

Pages are rendered at what the output needs — the canvas width once per column, doubled — rather
than at some fixed resolution and enlarged afterwards. PDF rendering is Apache PDFBox, so a
document it cannot lay out is one this cannot convert.

In the window the same thing is a grid drawn over the page: pick one, step through the pieces, and
click any cell to exclude it, because nobody wants the running header as one of their twelve
images. The window converts the page you are looking at; whole documents go through the command
line.

### Making it available

The installed application is the command line: arguments mean the CLI, no arguments mean the
window. `omniplotter setup` links it into `~/.local/bin` under that name — or, on Windows, writes a
shim and points at the console launcher, since the one the desktop starts has nowhere to print.
Where the link would not be found, it shows the line to add and offers to add it rather than editing
a shell's configuration on its own. `omniplotter doctor` prints where everything ended up.

The window can do the same, from the menu beside the convert button.

### Reading a file back apart

`inspect` un-inverts a Casio header, checks the sizes recorded in different places against each
other, undoes the CP obfuscation and inflates the pixel data. That is the question that actually
matters: whether the calculator will open the file.

## Building it

```bash
./setup.sh
```

Downloads a JDK and Maven into `tools/` and resolves every dependency into `.mvn/repo` and `libs/`.
Nothing is installed system-wide and your `~/.m2` is never touched, so the checkout is
self-contained and safe to delete.

Building and testing need nothing else. Regenerating the reference vectors additionally needs the
img2calc submodule, which a plain clone does not fetch:

```bash
git submodule update --init
```

```bash
./omniplotter.sh run          # open the app
./omniplotter.sh cli --help   # command line
./omniplotter.sh test         # test suite
./omniplotter.sh package      # native installer for this platform
```

These also work against a JDK and Maven already on your PATH, without `setup.sh` — which is how CI
runs them, so there is only one way to build.

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

## Layout

```
src/main/java/com/github/omniplotter/
  Main.java              arguments mean CLI, none means UI
  app/                   settings, logging, the update check, PATH setup
  cli/                   convert, formats, targets, grids, inspect, update, setup, doctor
  gui/                   JavaFX window, the crop and grid overlays
  engine/
    EngineApi            the one conversion path both faces use
    converter/           ImageOps (the ImageMagick operators), ImagePreprocessor (per-format pipeline)
    encoder/             CasioPicture, TIZ80, Python, Zero
    pdf/                 PdfPages, the only thing here that knows what a PDF is
    data/                Format, Target, ConversionOptions, Tiling, TileGrid, TilePlan
    inspect/             reads Casio files back apart
    util/                PixelBuffer, Palette, ByteSeq, EncoderUtils, Exif
tools/refgen/            the reference encoders and the golden-vector generator
tools/icon/              regenerates the app icon
reference/img2calc/      img2calc itself, as a pinned submodule
```

## Notes

Some behaviour is faithfully odd because the reference is: the TI checksum sums untruncated values
while the file stores truncated bytes, `im8c` drops literal pixels still buffered when the image
ends, and `zpic` wraps on the canvas width rather than the image width. These are reproduced
deliberately — the goal is files that behave exactly like img2calc's.

## Releases

Pushing a `v*` tag builds the installers on clean runners and attaches them to a GitHub Release,
together with the checksums and the command-line jar. The tag is the version: it is written into the
POM, and from there into the jar manifest, what `--version` prints, and the installer metadata.

What each release brought, and what has been deliberately ruled out, is in
[ROADMAP.md](ROADMAP.md).

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).

OmniPlotter is a derivative work of [TI-Planet's img2calc](https://github.com/TI-Planet/img2calc),
which is GPL-3.0, so it inherits that licence. The parts actually derived from it carry an
attribution header: the encoders, the ImageMagick-equivalent operators and preprocessing pipeline,
the format metadata ported from its tables, and the reference generator under `tools/refgen/`. The
CLI, the window and the build are original.

img2calc is by Xavier Andréani ([@critor](https://github.com/critor)) and Adrien Bertrand
([@Adriweb](https://github.com/Adriweb)). This port would not have been possible, or verifiable,
without their source being open.
