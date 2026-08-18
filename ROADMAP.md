# Roadmap

What is planned, in what order, and why. The version numbers are intent rather than promises and
there are no dates. Items move when they turn out to be harder, or less useful, than they looked.

One rule orders everything below: nothing ships that would disappoint on first use. It is the same
instinct as the golden vectors — a file the calculator refuses is worse than no file, and a feature
whose output cannot be read is worse than a missing feature.

## 1.0 — the first release

The engine is done and checked against the reference. What is missing is everything around it: the
parts that make this an installed application rather than a working build.

### Update notifications

A daily check against `https://api.github.com/repos/danieletoniolo/OmniPlotter/releases/latest`,
compared with the version the jar manifest records. When a newer one exists, the window shows a
dismissable banner and `omniplotter update` prints the same thing.

It notifies; it does not update itself. See [Decided against](#decided-against) for why, and why
that is not going to change.

Three constraints shape the implementation:

- Unauthenticated GitHub API calls are limited to 60 per hour per IP. One check per day, with the
  `ETag` from the previous response, stays far inside that and costs nothing when nothing changed.
- The check needs somewhere to keep that state, which is why persistent settings below are a
  prerequisite rather than a nicety.
- `Implementation-Version` is absent in a development build — [Cli.java](src/main/java/com/github/omniplotter/cli/Cli.java)
  already relies on this to print `(development build)`. The same signal switches the check off
  entirely, so working on the app never phones home.
- When the app was installed by a package manager, the notifier stays quiet and lets that package
  manager do its job. Installing through Homebrew and then being told by the app to go download a
  `.dmg` is the wrong answer twice.

### Terminal command setup

`omniplotter setup` puts the CLI on the user's `PATH`, and a button in the window does the same for
the people who installed a `.dmg` and would never discover from the interface that a CLI exists.

The installed launcher already is the CLI — [Main.java](src/main/java/com/github/omniplotter/Main.java)
reads arguments as the command line and their absence as the window — so this is only about making
the binary reachable under a sensible name. Per platform:

- **Windows** is the real work. The launcher jpackage produces is a GUI-subsystem executable, so
  `OmniPlotter.exe convert ...` in a console prints nothing at all. The fix is not `--win-console`,
  which would flash a console window every time someone opens the app normally, but a second
  launcher: `--add-launcher omniplotter=cli.properties` with `win-console=true`. That also gets the
  lower-case name a command line wants.
- **macOS and Linux** get a symlink in `~/.local/bin`, never `/usr/local/bin`, which would need
  `sudo`. If that directory is not on `PATH`, the command prints the exact line it proposes to add
  to the shell's startup file and asks first. Editing someone's shell configuration silently is not
  acceptable, however convenient.

While in there: shell completions, which picocli generates through `AutoComplete`, an `--uninstall`
that reverses all of it, and an `omniplotter doctor` that prints where every piece actually is.

### Persistent settings

Today the theme resets on every launch and so does the output directory. A single file in the
platform's configuration directory holds the theme, the last format, target and canvas, the output
directory, the window geometry and the update preference.

Small, and needed by the updater anyway.

### Input formats and orientation

[EngineApi](src/main/java/com/github/omniplotter/engine/EngineApi.java) decodes through `ImageIO`,
which does not read HEIC — the default for photographs taken on an iPhone — or WebP. The current
result is `Not a readable image`, on what is probably the most common first thing anyone tries.

The floor is an honest error that says which formats do work. The right fix is the TwelveMonkeys
ImageIO plugins for WebP and TIFF, plus honouring the EXIF orientation tag so photographs from a
phone do not arrive sideways.

### Logs, and a way to report a bug

A log file in the platform's log directory, an "Open log folder" item in the window, and issue
templates that ask for `omniplotter --version` and the image that failed. Cheap, and the difference
between a first release that produces usable reports and one that produces "it doesn't work".

### Release plumbing

Two of these have to be right in the first tag, because they cannot be fixed retroactively:

- `--win-upgrade-uuid`, with a fixed GUID. Without it every future `.msi` installs beside its
  predecessor instead of replacing it.
- A stable `--mac-package-identifier`.

And, because the builds are unsigned:

- `SHA256SUMS` generated in the publish job and attached to the release. It is the free and honest
  substitute for a signature.
- The fat jar published as a release asset. It is already built, and it is the way out for anyone
  who has a JDK 21, wants only the CLI, or is packaging this for a distribution.
- Accurate Gatekeeper instructions. On recent macOS versions the right-click → Open route no longer
  reliably appears for unsigned applications; the dependable path is to let the first launch be
  blocked and then use System Settings → Privacy & Security → "Open Anyway", with
  `xattr -dr com.apple.quarantine /Applications/OmniPlotter.app` as the terminal alternative. The
  README currently describes only the old route.

### One test for the CLI

There is none. The CLI is the public contract for everyone arriving from an img2calc URL, and a
snapshot test of its output is cheap insurance before anything below starts moving code around.

## 1.1 — legibility

The engine reproduces the reference exactly, including the parts of the reference that were tuned
for photographs. Text on a sixteen-colour screen wants the opposite treatment, and there is
currently no way to ask for it.

- **Dithering, exposed.** It already flows through the pipeline as a per-format boolean in
  [ImagePreprocessor](src/main/java/com/github/omniplotter/engine/converter/ImagePreprocessor.java);
  the work is threading an option through, not new image processing. On sixteen colours it is the
  difference between a legible picture and mush.
- **Brightness, contrast, gamma, saturation, unsharp** before quantisation, with *photo* and
  *document* presets over the top, since almost nobody wants to discover the right five numbers by
  hand.
- **Crop, and a fill mode.** A source whose aspect ratio does not match the canvas is currently
  letterboxed onto opaque white. Cover-and-centre-crop, plus a draggable crop rectangle on the
  preview, is the largest single improvement to the result for photographs.

Every new control defaults to what the reference does. `PreprocessingParityTest` stops meaning
anything the moment the default path stops being the reference path.

Crop lands here rather than later for a structural reason: it is the one-rectangle case of the
geometry stage that 1.2 needs N of.

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
