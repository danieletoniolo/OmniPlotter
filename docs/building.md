# Building from source

For anyone working on OmniPlotter itself: the toolchain, the layout of the code, and how a release
is made. [← Back to the README](../README.md)

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

How the output is checked against the original — and why that matters more here than usual — is in
[correctness.md](correctness.md).

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
src/main/resources/i18n/ the window's text, in English and Italian
tools/refgen/            the reference encoders and the golden-vector generator
tools/icon/              regenerates the app icon: tools/icon/make-icon.sh
reference/img2calc/      img2calc itself, as a pinned submodule
docs/                    these guides, and the screenshots the README shows
```

## Releases

Pushing a `v*` tag builds the installers on clean runners and attaches them to a GitHub Release,
together with the checksums and the command-line jar. The tag is the version: it is written into the
POM, and from there into the jar manifest, what `--version` prints, and the installer metadata.

What each release brought, and what has been deliberately ruled out, is in
[ROADMAP.md](../ROADMAP.md).
