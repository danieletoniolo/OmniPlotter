// OmniPlotter — convert images to calculator picture and script formats.
// Copyright (C) 2026 Daniele Toniolo
//
// Derived from TI-Planet's img2calc (https://github.com/TI-Planet/img2calc),
// by Xavier Andreani (@critor) and Adrien Bertrand (@Adriweb).
//
// Licensed under the GNU General Public License v3 or later. See LICENSE.

// Loads the reference's own copy of pako and exposes its deflate.
//
// The Casio formats wrap their pixels in a zlib stream, so the compressed bytes are part of what
// has to match. This used to call Node's zlib instead, on the grounds that both are zlib at the
// same settings — true on one machine, but not a promise across versions: regenerating the vectors
// under a different Node produced different bytes for some inputs, and CI caught it.
//
// pako is pure JavaScript, so it gives the same output everywhere, and it is what img2calc actually
// runs. Using it removes both the nondeterminism and the approximation.
//
// img2calc loads these files as plain <script> tags sharing one global scope, so they are
// concatenated and evaluated the same way here rather than imported.

import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const REFERENCE = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)), '../../reference/img2calc');

// The order index.html loads them in; deflate.js depends on everything before it.
const SCRIPTS = [
  'utils/common.js',
  'utils/strings.js',
  'zlib/constants.js',
  'zlib/adler32.js',
  'zlib/crc32.js',
  'zlib/zstream.js',
  'zlib/messages.js',
  'zlib/trees.js',
  'zlib/deflate.js',
  'deflate.js',
];

function load() {
  let source = '';
  for (const script of SCRIPTS) {
    const file = path.join(REFERENCE, script);
    if (!fs.existsSync(file)) {
      throw new Error(
        `The img2calc submodule is not checked out (missing ${script}).\n`
        + 'Run: git submodule update --init');
    }
    source += fs.readFileSync(file, 'utf8')
      // These files are written for both CommonJS and the browser; the require and module.exports
      // lines are inert in a browser and would throw here.
      .replace(/^\s*(const|var|let)\s+\{[^}]*\}\s*=\s*require\([^)]*\);?\s*$/gm, '')
      .replace(/^\s*(const|var|let)\s+\w+\s*=\s*require\([^)]*\);?\s*$/gm, '')
      .replace(/^\s*module\.exports.*$/gm, '')
      .replace(/^\s*'use strict';\s*$/gm, '') + '\n';
  }
  source += '\nglobalThis.__deflate = deflate_f;\n';

  const context = vm.createContext({ console });
  vm.runInContext(source, context);
  return context.__deflate;
}

const deflate_f = load();

/** img2calc's own {@code deflate_f}: a zlib stream at pako's defaults. */
export function deflate(bytes) {
  return Buffer.from(deflate_f(bytes, {}));
}
