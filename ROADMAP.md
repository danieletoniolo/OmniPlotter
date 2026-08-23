# Roadmap

What is planned, in what order, and why. The version numbers are intent rather than promises and
there are no dates. Items move when they turn out to be harder, or less useful, than they looked.

One rule orders everything below: nothing ships that would disappoint on first use. It is the same
instinct as the golden vectors — a file the calculator refuses is worse than no file, and a feature
whose output cannot be read is worse than a missing feature.

## Shipped

### 1.0 — the first release

Update notifications, `omniplotter setup` and `doctor`, persistent settings, WebP and TIFF input
with EXIF orientation honoured, a log file the window can open, release plumbing and a snapshot test
for the CLI.

Two things were built differently from how this file first described them, and the reasons are
worth keeping:

- The update check reads the tag out of the `Location` header of `/releases/latest` instead of
  parsing the JSON API. No dependency, no rate limit, and so none of the `ETag` machinery that was
  planned around the sixty-requests-an-hour budget.
- HEIC is not supported and will not be until a pure-Java decoder exists. It is named explicitly in
  the error rather than reported as an unreadable file.

Three things only the packaged application could reveal: `jlink` needs `java.net.http` *and*
`jdk.crypto.ec` or the check fails its TLS handshake; both NTFS and the macOS filesystem are
case-insensitive, so the console launcher cannot be called `omniplotter` beside `OmniPlotter`; and
`jpackage` accepts one to three integers as a version, with a first number of at least one.

### 1.1 — legibility

Dithering exposed as a three-way choice defaulting to what the reference does per format;
brightness, contrast, gamma, saturation and sharpening, with `photo` and `document` presets over
them; and filling and cropping, the crop drawn on the source preview and kept per image.

Every default is still the reference's behaviour, which is what keeps `PreprocessingParityTest`
meaningful. Filling decides proportions only — reaching the canvas is still `--enlarge-smaller`'s
job, as it was before.

## 1.2 — PDF input and tiling

Take a document, cut it into pieces that each fill the screen, and convert every piece. A page of
notes on a calculator is a different use case from a picture on a calculator, and it is the point
where this stops being a port of img2calc and becomes its own thing.

### Tiling is not a PDF feature

The split belongs in a geometry stage before preprocessing, independent of where the pixels came
from, so it works on a large scan just as well. PDF then adds only a decoder that turns pages into
`BufferedImage`s. Bolting a document-shaped path onto the converter would be the wrong shape and
would not survive the second format anyone asks for.

### The arithmetic, and why the interface has to show it

For an A4 page cut into `r` rows and `c` columns, each cell has aspect ratio `0.707 × r/c`. Matching
it to the target screen gives families rather than single answers:

| Screen | Grids that fit | Tiles | Effective DPI |
|---|---|---|---|
| 384 × 192 (Casio CG, 2:1) | 3 × 1 | 3 | 46 |
| | 6 × 2 | 12 | 93 |
| | 9 × 3 | 27 | 139 |
| 320 × 240 (NumWorks, TI-84 CE, 4:3) | 2 × 1 | 2 | 39 |
| | 4 × 2 | 8 | 77 |
| | 6 × 3 | 18 | 116 |

Three horizontal bands is very nearly a perfect fit for a 2:1 screen — cells of 2.12 against the
screen's 2.0, six percent of the height unused — and it is the obvious thing to reach for. It is
also useless for text, and the table says why: cutting horizontally does not add a single pixel
across the width of the page, so the resolution stays at 46 DPI whatever `r` is. Small print wants
something like 150. Resolution comes only from columns.

So a dense A4 page of text needs two or three columns, which means twelve tiles or twenty-seven, and
an interface that asks "how many parts?" hides exactly the number that decides whether the result
can be read. It should instead offer the grids that fit and, for each, how tall a line of text ends
up in pixels. That is the same bargain [OutputLimits](src/main/java/com/github/omniplotter/engine/data/OutputLimits.java)
already strikes for file sizes: find out here, not standing in front of the calculator.

### The details that decide whether it is usable

- **Overlap between tiles**, a few percent and configurable. Without it a line of text split down
  the middle is lost in both halves.
- **Render at the resolution the output needs**, deriving the DPI from the canvas width and the
  column count, at two to four times the target and then down through the existing pipeline.
  Rendering at a fixed low DPI and scaling up is the trap here, and it is the one that makes this
  kind of tool look bad.
- **Slots and names.** Twelve tiles do not fit in the ten `Pic` slots of a TI, and on a TI-73 there
  are three. Casio names cap at eight characters.
  [OnCalcName](src/main/java/com/github/omniplotter/engine/data/OnCalcName.java) has the rules
  already; what is needed is automatic numbering and a warning before the conversion, not after.
- **Multi-page documents**, with a `--pages 1-3,7` selection and an explicit count before starting,
  because ten pages at eight tiles is eighty files.
- **In the window**, the page with the grid drawn over it and individual tiles switched off, since
  nobody wants the running header as one of their twelve images.

Apache PDFBox does the rendering. It is Apache-2.0, which sits fine inside GPL-3.0, and adds four to
six megabytes to the fat jar. One warning for whoever implements this: the `jlink` module list in
[omniplotter.sh](omniplotter.sh) is maintained by hand, so a module PDFBox needs that is not in that
list fails **only in the packaged application**, never under `./omniplotter.sh run`. Verify on an
installer.

## 1.3 and beyond

Roughly in order of value for the effort, not commitments.

- **`extract`: the conversion backwards**, from a calculator file to a PNG. Most of it exists —
  [CasioFileInspector](src/main/java/com/github/omniplotter/engine/inspect/CasioFileInspector.java)
  already returns the inflated pixels, so what is missing is the palette and writing a PNG. It buys
  round-trip tests, an inspector tab in the window, and with jpackage's `--file-associations` a
  double-clicked `.g3p` that opens and explains itself. img2calc cannot do this.
- **Copy to the calculator.** Casio models mount as USB mass storage, so detecting the volume and
  offering a button is a small amount of work for something that feels like magic. Linking to TI
  calculators through `tilp` is a project in its own right and is not promised here.
- **Profiles**, saved and recalled by name, with `--profile` on the command line and a `--json`
  output mode for scripting.
- **Distribution channels.** A Homebrew cask in a personal tap first — it works immediately, unlike
  the official repository, and gives macOS users a real upgrade path. Then an AUR `PKGBUILD` and
  winget.
- **Folders dropped in and walked recursively**, conversions in parallel, cancellation, reveal in
  file manager.
- **Translations**, Italian and French alongside English. The community this is aimed at is largely
  French-speaking, and the tool it is a port of is French.
- **`inspect` for TI containers**, checksums included, which completes the story `extract` starts.
- **Animation.** Multiple frames into one generated script, and GIF input.
- **A headless smoke test for the window** in CI, through TestFX and Monocle.

An idea worth recording even though it is a separate project: for a page of pure text, instead of
rendering it as an image, extract the text with PDFBox and generate a script that draws it with the
calculator's own font. Perfectly sharp, and a fraction of the size. It breaks on formulae and
diagrams, so it would be an additional mode and never a replacement.

## Decided against

- **Code signing and notarisation.** Apple charges 99 USD a year and a Windows certificate that
  actually quiets SmartScreen costs several hundred more. This is a free program given away under
  the GPL. The consequences are accepted and documented instead: checksums to verify a download
  against, precise instructions for both operating systems, and the fat jar for anyone who would
  rather run the code than the installer.
- **Updating itself in place.** A downstream consequence of the above, and not a temporary state. A
  jpackage application cannot reliably replace itself, and doing it unsigned would mean asking users
  to trust a binary swap that nothing vouches for. The updater will always stop at telling you a new
  version exists, and either open the release page or leave the installer in your downloads folder.
- **A custom title bar.** Tried once and removed, and it is not coming back. It is the single thing
  that most makes a desktop application look out of place, and it cost a few hundred lines of
  hand-written resize and drag handling to look wrong.
