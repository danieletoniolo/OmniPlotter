# Installing OmniPlotter

For anyone who wants to use OmniPlotter rather than build it. [← Back to the README](../README.md)

## Download

The [releases page](https://github.com/danieletoniolo/OmniPlotter/releases/latest) carries an
installer for macOS, Windows and Linux, a `SHA256SUMS` to check a download against, and
`omniplotter-<version>-cli.jar` — the command line without the window, small and
platform-independent, for anyone who already has a JDK 21.

## The first launch

The builds are not signed, so the first launch is refused on both desktops. On macOS, open System
Settings → Privacy & Security after the refusal and choose "Open Anyway"; the right-click → Open
route is no longer reliable on recent versions. From a terminal,
`xattr -dr com.apple.quarantine /Applications/OmniPlotter.app` does the same. On Windows,
SmartScreen wants "More info" → "Run anyway".

## Checking a download

`SHA256SUMS` lists the checksum of every file on the release, which is what there is to verify
against in the absence of a signature. With the installer and `SHA256SUMS` in the same folder:

```bash
shasum -a 256 -c --ignore-missing SHA256SUMS
```

On Linux `sha256sum -c --ignore-missing SHA256SUMS` does the same. On Windows,
`Get-FileHash <installer> -Algorithm SHA256` prints the checksum to compare by eye.

## The command in a terminal

The installed application is the command line: arguments mean the CLI, no arguments mean the
window. `omniplotter setup` links it into `~/.local/bin` under that name — or, on Windows, writes a
shim and points at the console launcher, since the one the desktop starts has nowhere to print.
Where the link would not be found, it shows the line to add and offers to add it rather than editing
a shell's configuration on its own. `omniplotter doctor` prints where everything ended up.

The window can do the same, from the menu beside the convert button.

What the command line can do is in the [command line guide](cli.md).
