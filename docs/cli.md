# The command line

For converting from a terminal or a script, whole folders and whole documents at once.
[← Back to the README](../README.md)

The installed application is the command line as well: arguments mean the CLI, no arguments mean
the window. Getting `omniplotter` onto your PATH is in
[Installing](installing.md#the-command-in-a-terminal).

## Commands

| Command | What it does |
|---|---|
| `convert` | Convert one or more images to a calculator format. |
| `formats` | List output formats, with their canvas and colour limits. |
| `targets` | List calculator models and the formats they accept. |
| `grids` | Show the ways a page can be cut up for a screen, and how legible each is. |
| `inspect` | Read back a Casio picture file and report its structure. |
| `update` | Check whether a newer release is available. |
| `setup` | Make the omniplotter command available in a terminal. |
| `doctor` | Report where this installation keeps its pieces. |

`omniplotter <command> --help` lists every option of a command.

`update` checks straight away, whatever the window's daily schedule says. `update --auto=false`
turns that daily check off, and `update --auto` turns it back on.

## Converting

```bash
omniplotter convert photo.png -f cp.g3p --name PICT1
omniplotter convert *.jpg -f kandinsky.py -o ./out
omniplotter formats --target cg
omniplotter targets --mode var
omniplotter inspect PICT1.g3p
```

Format and target identifiers are the same strings img2calc uses in its URLs, so a link from the web
tool translates directly into a command here. Every model and its formats are listed in
[Supported calculators](calculators.md).

`--width` and `--height` set the canvas, or `--preset` picks one of the format's named sizes, such as
`full`, `graph` or `menu`. `--colors` sets the colour budget, and `--no-keep-ratio` stretches to the
canvas instead of preserving the proportions. `--name` sets the name on the calculator, and
`--number` the slot on formats that address pictures by number.

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

## Documents

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
18-point notes twelve pixels a line, which is a different answer. `--page` says what is being cut
up, when it is not A4: `letter`, `a5`, or millimetres as `210x297`.

`--pages 1-3,7` picks pages, `--overlap` sets how far tiles reach into each other so a line of text
falling on a cut survives in one of them, and `--start-slot` says where the numbering begins on
calculators that address pictures by number. Tiles are named `notes-p2-r3c1.g3p` on disk and
numbered `NOTES14` on the calculator, because eight characters is all there is in the second place.

Pages are rendered at what the output needs — the canvas width once per column, doubled — rather
than at some fixed resolution and enlarged afterwards. PDF rendering is Apache PDFBox, so a
document it cannot lay out is one this cannot convert.

In the window the same grid is drawn over the page, and tiles can be switched off one by one — see
[Using the app](app.md#a-document-in-tiles).

## Reading a file back apart

`inspect` un-inverts a Casio header, checks the sizes recorded in different places against each
other, undoes the CP obfuscation and inflates the pixel data. That is the question that actually
matters: whether the calculator will open the file. It reads `.g3p`, `.g4p` and `.c2p` files.
