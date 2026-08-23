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

### 1.2 — documents and tiling

PDF pages rendered at the resolution the output needs, cut into a grid of tiles that each fill the
screen, in both faces. `grids` reports what each way of cutting a page would give; the window draws
the grid over the page and lets cells be switched off.

Four things came out differently from how this section first described them:

- **This section planned 9×3 for three columns of A4, and the code offers 8×3.** Both waste about
  six percent of a 2:1 screen; the implementation picks, for each column count, the row count that
  wastes least. The resolution is unaffected either way — it depends only on the columns, which was
  the point the plan was making.
- **"Useless for text" was too strong, and shipping it as a verdict was a mistake.** The first
  version labelled grids "too small to read" from an assumed ten-point body text. Whether a grid
  works depends on the document as much as the screen: the same three bands that lose ten-point
  prose give eighteen-point notes twelve pixels a line, which reads perfectly well on a CG50. The
  figure is now quoted against a type size that is stated, with `--text-size` to change it.
- **Clicking a cell first excluded it**, which made it impossible to look around a grid: one gesture
  cannot both show you a piece and throw it away. Clicking now selects, stepping through the pieces
  has its own control, and excluding is a button that says so.
- **The window converts the page on screen**, not the whole document, and a cell switched off is
  switched off on that page alone. Both follow from the same thing: the choice is made by looking,
  so it cannot be applied to pages nobody has looked at. The command line is where a whole document
  goes through at once.

PDFBox needed no addition to the hand-maintained `jlink` list, unlike the previous two releases. It
costs four megabytes: the fat jar goes from 10 to 14, and the command-line jar from 1.5 to 5.1.

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
