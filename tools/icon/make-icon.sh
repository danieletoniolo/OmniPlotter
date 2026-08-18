#!/bin/bash
#
# Generates the application icon.
#
# The mark is a calculator screen showing a picture mid-conversion: the left half smooth, the right
# half reduced to a handful of flat colours. The right half is not drawn that way by hand — it is
# produced by actually running the reduction, so the icon is a small example of what the app does.
#
# Outputs are committed, so this only needs re-running when the design changes.

set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=src/main/resources/icon
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

S=1024                     # master size; everything scales down from here
SW=$((S * 664 / 1024))     # screen width
SH=$((S * 456 / 1024))     # screen height

# The picture on the screen: a sky gradient with a sun low over the horizon.
magick -size ${SW}x${SH} gradient:'#2f6491-#f0c97e' \
    -fill '#d9762f' -draw "circle $((SW*30/100)),$((SH*62/100)) $((SW*30/100)),$((SH*22/100))" \
    -fill '#f7e8c8' -draw "circle $((SW*30/100)),$((SH*40/100)) $((SW*30/100)),$((SH*22/100))" \
    "$WORK/scene.png"

# Put the right half through what the app actually does to an image: shrink it to a calculator-sized
# raster and cut it to a handful of colours. Scaling back up with nearest-neighbour keeps the pixels
# visible, which is the point — the two halves are the same picture before and after conversion.
magick "$WORK/scene.png" -crop $((SW/2))x${SH}+$((SW/2))+0 +repage \
    -resize 14x10! +dither -colors 5 \
    -scale $((SW/2))x${SH}! "$WORK/right.png"
magick "$WORK/scene.png" "$WORK/right.png" -geometry +$((SW/2))+0 -composite "$WORK/screen.png"

# A hairline where the two halves meet, so the split reads as intentional.
magick "$WORK/screen.png" -fill '#0e1116' -draw "rectangle $((SW/2-2)),0 $((SW/2+1)),${SH}" \
    "$WORK/screen.png"

# Round the screen corners.
magick -size ${SW}x${SH} xc:none -fill white \
    -draw "roundrectangle 0,0 $((SW-1)),$((SH-1)) $((S*14/1024)),$((S*14/1024))" "$WORK/mask.png"
magick "$WORK/screen.png" "$WORK/mask.png" -alpha off -compose CopyOpacity -composite "$WORK/screen.png"

# The body, its bezel, and a suggestion of keys underneath.
magick -size ${S}x${S} xc:none \
    -fill '#2a2f36' -draw "roundrectangle $((S*56/1024)),$((S*56/1024)) $((S*968/1024)),$((S*968/1024)) $((S*192/1024)),$((S*192/1024))" \
    -fill '#0e1116' -draw "roundrectangle $((S*144/1024)),$((S*208/1024)) $((S*880/1024)),$((S*720/1024)) $((S*36/1024)),$((S*36/1024))" \
    "$WORK/body.png"

magick "$WORK/body.png" "$WORK/screen.png" \
    -geometry +$((S*180/1024))+$((S*236/1024)) -composite "$WORK/icon.png"

magick "$WORK/icon.png" -fill '#575f6a' \
    -draw "roundrectangle $((S*236/1024)),$((S*788/1024)) $((S*388/1024)),$((S*852/1024)) $((S*20/1024)),$((S*20/1024))" \
    -draw "roundrectangle $((S*412/1024)),$((S*788/1024)) $((S*564/1024)),$((S*852/1024)) $((S*20/1024)),$((S*20/1024))" \
    -draw "roundrectangle $((S*588/1024)),$((S*788/1024)) $((S*740/1024)),$((S*852/1024)) $((S*20/1024)),$((S*20/1024))" \
    -draw "roundrectangle $((S*764/1024)),$((S*788/1024)) $((S*860/1024)),$((S*852/1024)) $((S*20/1024)),$((S*20/1024))" \
    "$OUT/icon.png"

# Platform bundles. jpackage wants .icns on macOS, .ico on Windows, and a .png on Linux.
if command -v iconutil >/dev/null 2>&1; then
    ICONSET="$WORK/OmniPlotter.iconset"
    mkdir -p "$ICONSET"
    for sz in 16 32 128 256 512; do
        magick "$OUT/icon.png" -resize ${sz}x${sz} "$ICONSET/icon_${sz}x${sz}.png"
        magick "$OUT/icon.png" -resize $((sz*2))x$((sz*2)) "$ICONSET/icon_${sz}x${sz}@2x.png"
    done
    iconutil -c icns "$ICONSET" -o "$OUT/icon.icns"
fi

magick "$OUT/icon.png" -define icon:auto-resize=256,128,64,48,32,16 "$OUT/icon.ico"

echo "Wrote:"
ls -la "$OUT" | tail -n +2 | awk '{printf "  %-14s %s\n", $9, $5}'
