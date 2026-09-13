# What shipped, and what did not

What each release brought — including the parts that came out differently from how they were
planned, which are the ones worth keeping — and the things deliberately ruled out.

Ideas not committed to are not here. They live in a notebook outside the repository, where one can
be written down, reconsidered and dropped without ever having been read as a promise.

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

### 1.2.1 — fixes, and a window that explains itself

A point release: nothing here converts anything 1.2 could not. It is fixes, and the window reworked
around what 1.2 added. The output panel became a numbered timeline that fills in as a file arrives,
one step open at a time; there is a short tour and a panel of concepts behind an info button; the
commands the window proposes can be copied, with an offer to add the PATH line rather than only
showing it; and the window reads in Italian as well as English.

Most of it was finding out that things did not work:

- **The update check had never fired, and the code was right.** The repository is private, so an
  anonymous request for `/releases/latest` answers 404 and the window correctly says nothing. What
  was wrong around it: a failed check wrote the twenty-four-hour timestamp *before* the request, so
  being offline at launch used the day up; its only diagnostic went to `FINE` while the log keeps
  `INFO`; and the managed-install guard read the JVM's path, so a Homebrew or distribution JDK
  switched the check off for every jar-based run.
- **Bold text was being synthesised.** Neither the theme nor this project names a font family, and
  the platform's hidden system font has no bold face JavaFX can find, so it inked the regular one
  heavier and left the advances alone: 116.13px regular against 116.13px bold, measured. Naming a
  family gets a real cut. Helvetica Neue is not that family — on macOS its bold resolves to the
  *Condensed* face.
- **A box will not go below the sum of its children's minimums.** The first version of the step
  animation drove the preferred height to zero, which a column of sliders simply ignored: it sat at
  full size and moved in the last few frames. That is what a jerk is.
- **A trackpad does not send a wheel.** It sends a gesture of many small deltas and then a tail of
  inertia that runs for a second, which counted as wheel crossed three steps for one flick.
- **Steps that do not apply were first left openable**, on the grounds that the sequence should
  suggest rather than lock. Wrong: with nothing to convert, clicking a dim heading put a panel of
  settings on screen for a conversion with no subject. Dimming has to mean unavailable.

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
