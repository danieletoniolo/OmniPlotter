// Minimal PNG reader for the reference generator.
//
// It exists so refgen can decode tmp/palcp.png and tmp/pal8ci.png on its own. The browser tool
// reads those palettes through a <canvas>, which yields every pixel of the image as RGBA; loading
// them via the Java side instead would make the reference agree with the port by construction and
// prove nothing.
//
// Scope: 8-bit, non-interlaced, colour types 0/2/3/4/6. That covers the palette files and the
// fixtures refgen writes; anything else throws rather than guessing.

import zlib from 'node:zlib';
import fs from 'node:fs';

const CHANNELS = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 };

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  if (pb <= pc) return b;
  return c;
}

/** Decode a PNG file to `{ width, height, rgba }`, rgba being a row-major Uint8Array of w*h*4. */
export function readPng(path) {
  const buf = fs.readFileSync(path);
  if (buf.readUInt32BE(0) !== 0x89504e47 || buf.readUInt32BE(4) !== 0x0d0a1a0a) {
    throw new Error(`${path}: not a PNG`);
  }

  let width = 0, height = 0, depth = 0, colorType = 0, interlace = 0;
  let palette = null, transparency = null;
  const idat = [];

  for (let pos = 8; pos < buf.length; ) {
    const len = buf.readUInt32BE(pos);
    const type = buf.toString('ascii', pos + 4, pos + 8);
    const data = buf.subarray(pos + 8, pos + 8 + len);
    pos += 12 + len; // length + type + data + crc

    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      depth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === 'PLTE') {
      palette = data;
    } else if (type === 'tRNS') {
      transparency = data;
    } else if (type === 'IDAT') {
      idat.push(data);
    } else if (type === 'IEND') {
      break;
    }
  }

  if (depth !== 8) throw new Error(`${path}: only 8-bit PNGs supported, got ${depth}`);
  if (interlace !== 0) throw new Error(`${path}: interlaced PNGs not supported`);
  const nch = CHANNELS[colorType];
  if (!nch) throw new Error(`${path}: unsupported colour type ${colorType}`);

  const raw = zlib.inflateSync(Buffer.concat(idat));
  const stride = width * nch;
  const pixels = Buffer.alloc(height * stride);

  // Undo the per-scanline filters. Each scanline is prefixed with its filter byte.
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    const src = raw.subarray(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    const cur = pixels.subarray(y * stride, (y + 1) * stride);
    const prev = y > 0 ? pixels.subarray((y - 1) * stride, y * stride) : null;

    for (let i = 0; i < stride; i++) {
      const a = i >= nch ? cur[i - nch] : 0;
      const b = prev ? prev[i] : 0;
      const c = prev && i >= nch ? prev[i - nch] : 0;
      let v = src[i];
      switch (filter) {
        case 0: break;
        case 1: v += a; break;
        case 2: v += b; break;
        case 3: v += (a + b) >> 1; break;
        case 4: v += paeth(a, b, c); break;
        default: throw new Error(`${path}: bad filter ${filter} on row ${y}`);
      }
      cur[i] = v & 0xFF;
    }
  }

  // Expand to RGBA, the shape a canvas would have handed the browser tool.
  const rgba = new Uint8Array(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    const s = i * nch, d = i * 4;
    switch (colorType) {
      case 0: rgba[d] = rgba[d + 1] = rgba[d + 2] = pixels[s]; rgba[d + 3] = 255; break;
      case 2: rgba[d] = pixels[s]; rgba[d + 1] = pixels[s + 1]; rgba[d + 2] = pixels[s + 2]; rgba[d + 3] = 255; break;
      case 4: rgba[d] = rgba[d + 1] = rgba[d + 2] = pixels[s]; rgba[d + 3] = pixels[s + 1]; break;
      case 6: rgba[d] = pixels[s]; rgba[d + 1] = pixels[s + 1]; rgba[d + 2] = pixels[s + 2]; rgba[d + 3] = pixels[s + 3]; break;
      case 3: {
        const idx = pixels[s];
        rgba[d] = palette[idx * 3];
        rgba[d + 1] = palette[idx * 3 + 1];
        rgba[d + 2] = palette[idx * 3 + 2];
        rgba[d + 3] = transparency && idx < transparency.length ? transparency[idx] : 255;
        break;
      }
    }
  }

  return { width, height, rgba };
}

/** Write an RGBA buffer as an uncompressed-filter PNG. Used for the preprocessing parity fixtures. */
export function writePng(path, width, height, rgba) {
  const stride = width * 4;
  const raw = Buffer.alloc(height * (stride + 1));
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: None
    Buffer.from(rgba.buffer, rgba.byteOffset + y * stride, stride).copy(raw, y * (stride + 1) + 1);
  }

  const chunk = (type, data) => {
    const out = Buffer.alloc(12 + data.length);
    out.writeUInt32BE(data.length, 0);
    out.write(type, 4, 'ascii');
    data.copy(out, 8);
    out.writeInt32BE(zlib.crc32(out.subarray(4, 8 + data.length)) | 0, 8 + data.length);
    return out;
  };

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // colour type: RGBA
  fs.writeFileSync(path, Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]));
}
