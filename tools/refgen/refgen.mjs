#!/usr/bin/env node
// Generates the golden vectors the Java encoders are checked against.
//
//   node tools/refgen/refgen.mjs        (or ./omniplotter.sh refgen)
//
// For every (format x pattern x size) it runs the reference encoder from img2calc.mjs and writes
// the resulting bytes to src/test/resources/ref/vectors/, alongside the exact RGBA input in
// ref/images/ so the Java test feeds its encoder the identical pixels. ReferenceVectorTest then
// asserts byte equality.
//
// The inputs are synthetic on purpose: this stage checks the *encoders*, which take a pixel buffer
// and produce a container. Resizing and quantisation are checked separately by
// PreprocessingParityTest, which does need real ImageMagick.

import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readPng } from './png.mjs';
import * as ref from './img2calc.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const OUT = path.join(ROOT, 'src/test/resources/ref');

// The target matters for a handful of formats: it switches import lines, the ti_draw_ce fill_rect
// offsets, and whether stringToPythonBytes may emit octal escapes. These are the defaults the
// reference lands on for each format (index.html:212-256).
const DEFAULT_TARGET = {
  'im8c.8xv': '8xpython', '8ca': '8xcolor', '8ci': '8xcolor', '8xi': '8xp',
  '83i': '83', '73i': '73', '82i': '82', '85i': '85', '86i': '86',
  'zpic': 'zero',
  'c2p': 'cp2', 'i.c2p': 'cp2',
  'cp.g3p': 'cg', 'cp01.g3p': 'cg', 'cp_i.g3p': 'cg', 'cp01_i.g3p': 'cg',
  'cp01.g4p': 'cg3', 'cp01_i.g4p': 'cg3',
  'ti_graphics.py': '8xpython', 'ti_draw_ce.py': '8xpython', 'ti_draw_cx.py': 'cx2',
  'graphic.py': 'nw110', 'graphic_ns.py': 'ns', 'graphic_cg.py': 'cg', 'graphic_g3.py': 'g3',
  'gint_cg.py': 'cg', 'gint_g3.py': 'g3',
  'nsp_cx.py': 'cx', 'nsp_ns.py': 'ns',
  'casioplot_cg.py': 'cg2', 'casioplot_g3.py': 'g3',
  'kandinsky.py': 'nw110', 'kandinsky_cg.py': 'cg',
  'hpprime.py': 'prime',
  'microbit.py': '8xpython', 'ti_hub_mb.py': '8xpython', 'ti_hub_rgbarr.py': '8xpython',
};

const PALETTE_FILE = {
  '8ci': 'pal8ci.png',
  'i.c2p': 'palcp.png', 'cp_i.g3p': 'palcp.png',
  'cp01_i.g3p': 'palcp.png', 'cp01_i.g4p': 'palcp.png',
};

// Each VAR format's native full-screen size, from configForFormat (index.html:141). Used for the
// realistic-size vectors, so those stay in spec instead of bloating the fixtures with sizes the
// format could never carry. zpic is the exception: it spends 10 bytes per pixel, so it gets a
// mid-size case rather than its 320x195 maximum.
const NATIVE_SIZE = {
  'im8c.8xv': [320, 210], '8ca': [134, 83], '8ci': [266, 165],
  '8xi': [96, 63], '83i': [96, 63], '73i': [96, 63], '82i': [96, 63],
  '85i': [128, 63], '86i': [128, 63],
  'c2p': [320, 528], 'i.c2p': [320, 528],
  'cp.g3p': [384, 192], 'cp01.g3p': [384, 192], 'cp01.g4p': [384, 192],
  'cp_i.g3p': [384, 192], 'cp01_i.g3p': [384, 192], 'cp01_i.g4p': [384, 192],
  'zpic': [95, 63],
};

// --- synthetic inputs --------------------------------------------------------------------------

// xorshift32, so the "noise" pattern is identical on every machine and every run.
function prng(seed) {
  let s = seed >>> 0;
  return () => {
    s ^= s << 13; s >>>= 0;
    s ^= s >>> 17;
    s ^= s << 5; s >>>= 0;
    return s;
  };
}

const PATTERNS = {
  // One colour: drives the RLE degenerate case where the palette has a single entry (nbits == 0).
  solid: (w, h) => {
    const px = new Uint8Array(w * h * 4);
    for (let i = 0; i < w * h; i++) px.set([0x20, 0x80, 0xC0, 0xFF], i * 4);
    return px;
  },
  // Smooth ramp: many colours, long horizontal runs.
  gradient: (w, h) => {
    const px = new Uint8Array(w * h * 4);
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++)
        px.set([
          w > 1 ? Math.round(x * 255 / (w - 1)) : 0,
          h > 1 ? Math.round(y * 255 / (h - 1)) : 0,
          128, 0xFF,
        ], (y * w + x) * 4);
    return px;
  },
  // Three flat colours with run boundaries on the diagonal.
  diagonal: (w, h) => {
    const px = new Uint8Array(w * h * 4);
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const c = x === y ? [255, 0, 0] : x === y + 1 ? [0, 255, 0] : [0, 0, 255];
        px.set([...c, 0xFF], (y * w + x) * 4);
      }
    return px;
  },
  // Transparent holes: exercises the zpic alpha skip and the RLE transparent-index detection.
  alpha: (w, h) => {
    const px = new Uint8Array(w * h * 4);
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const transparent = ((x >> 1) + (y >> 1)) % 2 === 0;
        px.set([
          w > 1 ? Math.round(x * 255 / (w - 1)) : 0,
          64, 200,
          transparent ? 0 : 0xFF,
        ], (y * w + x) * 4);
      }
    return px;
  },
  // Worst case for RLE: almost every pixel its own palette entry, pushing nbits high.
  noise: (w, h) => {
    const px = new Uint8Array(w * h * 4);
    const rnd = prng(0x1234567);
    for (let i = 0; i < w * h; i++) {
      const v = rnd();
      px.set([v & 0xFF, (v >> 8) & 0xFF, (v >> 16) & 0xFF, 0xFF], i * 4);
    }
    return px;
  },
};

// Small sizes get the full cross product. 13x7 is deliberately odd in both axes and not a multiple
// of 8, so it catches the 2-pixels-per-byte and 8-pixels-per-byte packing edges.
const SMALL_SIZES = [[1, 1], [13, 7], [12, 8]];
// Patterns used at native size. Script formats are left out of this pass — a full-screen noise
// image as a Python source file runs to megabytes and adds no coverage over the small matrix.
const LARGE_PATTERNS = ['gradient', 'diagonal'];

// --- run -------------------------------------------------------------------------------------

const palettes = {
  'pal8ci.png': readPng(path.join(ROOT, 'tmp/pal8ci.png')),
  'palcp.png': readPng(path.join(ROOT, 'tmp/palcp.png')),
};
for (const [name, p] of Object.entries(palettes)) {
  console.log(`palette ${name}: ${p.width}x${p.height} = ${p.width * p.height} colours`);
}

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(path.join(OUT, 'images'), { recursive: true });
fs.mkdirSync(path.join(OUT, 'vectors'), { recursive: true });

const slug = (f) => f.replace(/[./]/g, '_');
const cases = [];
for (const [w, h] of SMALL_SIZES)
  for (const pattern of Object.keys(PATTERNS))
    for (const format of ref.ALL_FORMATS) cases.push({ format, pattern, w, h });
for (const [format, [w, h]] of Object.entries(NATIVE_SIZE))
  for (const pattern of LARGE_PATTERNS) cases.push({ format, pattern, w, h });

const manifest = [];
const writtenImages = new Set();
let bytes = 0;

for (const { format, pattern, w, h } of cases) {
  // Raw RGBA rather than PNG: no decoder in the loop means no question about how alpha, colour
  // profiles or premultiplication were round-tripped. gzip only to keep the fixtures small.
  const imageName = `${pattern}_${w}x${h}.rgba.gz`;
  const px = PATTERNS[pattern](w, h);
  if (!writtenImages.has(imageName)) {
    fs.writeFileSync(path.join(OUT, 'images', imageName), zlib.gzipSync(Buffer.from(px), { level: 9 }));
    writtenImages.add(imageName);
  }

  const paletteFile = PALETTE_FILE[format] ?? null;
  ref.configure({
    format,
    target: DEFAULT_TARGET[format],
    paletteArray: paletteFile ? palettes[paletteFile].rgba : [],
    inFileName: 'TESTVAR.png',
    reqWidth: w,
    reqHeight: h,
    oncalcName: 'TESTVAR',
    oncalcNum: 1,
  });

  const { bytes: out, calcName } = ref.encode({ width: w, height: h }, px);
  const vector = `${slug(format)}__${pattern}_${w}x${h}.bin`;
  fs.writeFileSync(path.join(OUT, 'vectors', vector), Buffer.from(out));
  bytes += out.length;

  manifest.push([
    format, DEFAULT_TARGET[format], pattern, w, h,
    paletteFile ?? '-', `images/${imageName}`, `vectors/${vector}`,
    'TESTVAR', 1, calcName,
  ].join('\t'));
}

// TSV rather than JSON: every field is a flat scalar, and this keeps the test side free of a JSON
// parser dependency.
const HEADER = ['format', 'target', 'pattern', 'width', 'height', 'palette', 'image', 'vector',
  'oncalcName', 'oncalcNum', 'calcName'].join('\t');
fs.writeFileSync(path.join(OUT, 'manifest.tsv'), [HEADER, ...manifest].join('\n') + '\n');
console.log(`${manifest.length} vectors, ${writtenImages.size} images, ${(bytes / 1024 / 1024).toFixed(1)} MiB`);
