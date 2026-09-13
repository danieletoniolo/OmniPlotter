<p align="center">
  <img src="src/main/resources/icon/icon.png" width="120" alt="OmniPlotter">
</p>

<h1 align="center">OmniPlotter</h1>

<p align="center">
  <b>Put your pictures and your notes on a graphing calculator.</b><br>
  Images and PDFs in, files your Casio, TI, NumWorks or HP opens out.
</p>

<p align="center">
  <a href="../../releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/danieletoniolo/OmniPlotter?label=release&color=1f6feb"></a>
  <a href="../../actions/workflows/ci.yml"><img alt="CI" src="https://github.com/danieletoniolo/OmniPlotter/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="macOS, Windows and Linux" src="https://img.shields.io/badge/platform-macOS%20%7C%20Windows%20%7C%20Linux-555">
  <a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue"></a>
</p>

<p align="center">
  <a href="../../releases/latest"><b>Download</b></a> &nbsp;·&nbsp;
  <a href="#guides">Guides</a> &nbsp;·&nbsp;
  <a href="docs/calculators.md">Supported calculators</a>
</p>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/tiles-dark.png">
  <img alt="A page of lecture notes cut into twelve tiles, one of them shown exactly as the calculator will display it" src="docs/screenshots/tiles-light.png">
</picture>

## See the result before anything is written

The preview beside your picture is the output itself — the same pixels the encoder is about to
write — so the dithering, the colour banding, and a file too big for the calculator are all there to
judge before anything exists.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/photo-dark.png">
  <img alt="A fractal image beside its calculator preview, with the picture controls open" src="docs/screenshots/photo-light.png">
</picture>

## Notes you can actually read

A whole A4 page on a 384-pixel screen is a postage stamp. OmniPlotter cuts PDFs and large scans into
tiles that each fill the screen, and tells you what each grid gives: the dots per inch it reaches,
and how tall a line of type ends up.

## A window that explains itself

The panel asks its questions in the order a conversion happens, and only the ones that apply to what
you dropped. A short tour and a panel of concepts explain the rest. In English and Italian, light or
dark.

<p align="center">
  <img alt="The tour pointing at the two previews" src="docs/screenshots/tour.png" width="85%">
</p>

## Files the calculator will open

Every encoder is checked byte for byte against [TI-Planet's img2calc](https://tiplanet.org/forum/img2calc.php):
591 golden vectors, every format, all matching — [how](docs/correctness.md).

Works with **Casio · TI · NumWorks · HP · Zero** — [every model and format](docs/calculators.md).

## Download

Installers for macOS, Windows and Linux are on the [releases page](../../releases/latest). The
builds are not signed, so the first launch is refused —
[here is how to open it anyway](docs/installing.md#the-first-launch).

The installed app is a command line as well:

```bash
omniplotter convert photo.png -f cp.g3p --name PICT1
omniplotter convert notes.pdf -f cp.g3p --look document --grid 8x3 -o ./tiles
omniplotter targets
```

## Guides

- [Installing](docs/installing.md) — downloads, the first launch, the command in a terminal
- [Using the app](docs/app.md) — the window, from dropping a file in to writing it out
- [Command line](docs/cli.md) — converting, documents and grids, reading a file back apart
- [Supported calculators](docs/calculators.md) — every model and the formats it takes
- [Building from source](docs/building.md) — the toolchain, the code, how a release is made
- [How the output is checked](docs/correctness.md) — golden vectors and the parity test
- [What shipped](ROADMAP.md) — each release, and what was ruled out on purpose

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
