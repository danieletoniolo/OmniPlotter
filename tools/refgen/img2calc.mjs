// Verbatim extraction of the img2calc encoders from tmp/index.html.
//
// The function bodies below are transcribed as literally as possible from the reference so that a
// mismatch against the Java port is always a Java bug and never a transcription artefact. Only
// three things are substituted, all of them browser-isms with no bearing on the output bytes:
//
//   deflate_f(x, {})  ->  zlib.deflateSync    (identical: pako defaults are windowBits 15,
//                                              non-raw, level 6 — the same zlib stream)
//   prompt(...)       ->  the `oncalcName` / `oncalcNum` module globals
//   getWidth()        ->  the `reqWidth` module global
//
// The reference reads `format`, `target`, `global_paletteArray` and `global_inFileName` from module
// scope, so they stay module-scoped here too; `configure()` sets them before each call.
//
// Source line numbers refer to tmp/index.html.

import zlib from 'node:zlib';

// --- globals the reference functions close over ------------------------------------------------

export let format = '';
export let target = '';
let global_paletteArray = [];
let global_inFileName = '';
let reqWidth = 0;
let reqHeight = 0;
let oncalcName = '';
let oncalcNum = 1;

export function configure(opts) {
  format = opts.format;
  target = opts.target;
  global_paletteArray = opts.paletteArray ?? [];
  global_inFileName = opts.inFileName ?? 'TESTVAR.png';
  reqWidth = opts.reqWidth ?? 0;
  reqHeight = opts.reqHeight ?? 0;
  oncalcName = opts.oncalcName ?? 'TESTVAR';
  oncalcNum = opts.oncalcNum ?? 1;
}

function deflate_f(input) {
  return zlib.deflateSync(Buffer.from(input));
}

// --- helpers (index.html:658-700, 1723-1790) ---------------------------------------------------

function color2int(col, r, g, b, a) {
    return col[2] >> (8 - r) | col[1] >> (8 - g) << r | col[0] >> (8 - b) << (r + g) | col[3] >> (8 - a) << (r + g + b);
}

function getPixel_raw(img, i) {
    i *= 4;
    return [img[i], img[i + 1], img[i + 2], img[i + 3]];
}

function getPixel_RGBA_int(img, i, r, g, b, a) {
    i *= 4;
    return img[i + 2] >> (8 - r) | img[i + 1] >> (8 - g) << r | img[i] >> (8 - b) << (r + g) | img[i + 3] >> (8 - a) << (r + g + b);
}

function simpl_byte(val, bits) {
    return (val >> (8 - bits)) << (8 - bits);
}

function simplPixel_RGBA(col, r, g, b, a) {
    return new Array(simpl_byte(col[0], r), simpl_byte(col[1], g), simpl_byte(col[2], b), simpl_byte(col[3], a));
}

function getPixel_RGBA_array(img, i, r, g, b, a) {
    i *= 4;
    return new Array(simpl_byte(img[i + 0], r), simpl_byte(img[i+1], g), simpl_byte(img[i+2], b), simpl_byte(img[i+3], a));
}

function indexOfArrayInArray(item, array) {
    for (var i = 0; i < array.length; i++) {
        if(array[i].length==item.length) {
            let test = true;
            for(var k=0; k<item.length && test; k++)
                if(array[i][k] != item[k])
                    test = false;
            if(test) return i;
        }
    }
    return -1;
}

function crcti8x(data) {
    return data.reduce((a, b) => a + b, 0) & 0xFFFF;
}

function padStr(str, n, char) {
    str = str.substring(0, n);
    while (str.length < n)
        str += char;
    return str;
}

function int2BigEndianArray(v, n) {
    return int2LittleEndianArray(v, n).reverse();
}

function int2LittleEndianArray(v, n) {
    const array = [];
    for (let i = 0; i < n; i++) {
        array.push(v & 0xff);
        v >>= 8;
    }
    return array;
}

function str2charCodeArray(str, n=0) {
    let array = [];
    let l = str.length;
    if (n <= 0) n = l;
    for (let i = 0; i < l; i++)
        array.push(str.charCodeAt(i));
    for (let i = 0; i < n - l; i++)
        array.push(0);
    return array;
}

function binaryInvertArray(arr) {
    arr = new Uint8Array(arr);
    for(let i = 0; i < arr.length; i++)
        arr[i] = ~arr[i];
    return Array.from(arr);
}

function binarySwapArray(arr,n1,n2) {
    let mask1 = (1 << n1) - 1;
    let mask2 = ((1 << n2) - 1) << n1;
    for(let i = 0; i < arr.length; i++)
        arr[i] = ((arr[i]&mask1)<<n2)|((arr[i]&mask2)>>n1);
    return arr;
}

const nearestiRGB = function(color, palette) {
    let min = 4 * 255 + 1;
    let imin = -1;
    for (let i = 0; i < palette.length; i++) {
        const v = Math.abs(palette[i][0] - color[0]) + Math.abs(palette[i][1] - color[1]) + Math.abs(palette[i][2] - color[2]) + Math.abs(palette[i][3] - color[3]);
        if (v < min) {
            imin = i;
            min = v;
        }
    }
    return imin;
}

// adler32 is kept only to preserve the reference's exact (no-op) behaviour: it returns a Number,
// and Array.from(<Number>) is [], so the concat below appends nothing. The real Adler-32 trailer
// comes from the zlib wrapper.
const adler32 = (adler, buf, len, pos) => {
  let s1 = (adler & 0xffff) |0,
      s2 = ((adler >>> 16) & 0xffff) |0,
      n = 0;
  while (len !== 0) {
    n = len > 2000 ? 2000 : len;
    len -= n;
    do {
      s1 = (s1 + buf[pos++]) |0;
      s2 = (s2 + s1) |0;
    } while (--n);
    s1 %= 65521;
    s2 %= 65521;
  }
  return (s1 | (s2 << 16)) |0;
};

// --- stringToPythonBytes (index.html:698) ------------------------------------------------------

function stringToPythonBytes(s, lmax) {
  let n = s.length;
  let st = "";
  let ng1 = 0;
  let ng2 = 0;
  let sout = "";
  let lout = "";
  for(let i=0; i<n; i++) {
    let v = s.charCodeAt(i);
    if(v == 34) ng2++; // count " chars
    else if(v == 39) ng1++; // count ' chars
  }
  for(let i=0; i<n; i++) {
    let v = s.charCodeAt(i);
    let nextv = -1;
    if(i < n - 1) nextv = s.charCodeAt(i + 1);
    let cout = "";
    if(target != '8xonline' && v == 7) cout = "\\a";
    else if(v == 8) cout = "\\b";
    else if(v == 9) cout = "\\t";
    else if(v == 10) cout = "\\n";
    else if(v == 11) cout = "\\v";
    else if(v == 12) cout = "\\f";
    else if(v == 13) cout = "\\r";
    else if(v == 92) cout = "\\\\";
    else if(v == 34 && ng2 <= ng1) cout = '\\"'; // escape ' chars
    else if(v == 39 && ng2 > ng1) cout = "\\'"; // escape " chars
    else if(v >= 32 && v <= 0x7E) cout = String.fromCharCode(v); // printable chars
    else if(target != '8xonline' && v <= 0o77 && (nextv < 48 || nextv > 55)) cout = "\\" + v.toString(8); // octal notation if next char is not in 0-7 // not supported on maclasseti.fr
    else { // hexadecimal notation
      let hex = v.toString(16);
      if(v < 0x10) hex = "0" + hex;
      cout = "\\x" + hex;
    }
    if(lmax >= 7 && lout.length + cout.length + 3 >= lmax) {
      if(ng2 > ng1) lout = "b'" + lout + "'";
      else lout = 'b"' + lout + '"';
      sout += lout + "\n";
      lout = "";
    }
    lout += cout;
  }
  if(ng2 > ng1) lout = "b'" + lout + "'";
  else lout = 'b"' + lout + '"';
  sout += lout + "\n";
  return sout;
}

// --- handleOutImgPythonRGBARR (index.html:746) -------------------------------------------------

export function handleOutImgPythonRGBARR(img, img_a) {
    let im = "";
    for(let y=0; y<img.height; y++)
      for(let x=0; x<img.width; x++) {
        let j = y*img.width + x;
        let color = getPixel_RGBA_array(img_a, j, 8, 8, 8, 1);
        if(color[3]>0)
          im += String.fromCharCode(j^7) + String.fromCharCode(color[0]) + String.fromCharCode(color[1]) + String.fromCharCode(color[2]);
      }
    let python = "";
    python += "#image converted on TI-Planet\n#tiplanet.org/img2calc\n\n";
    if (target === '8xpython')
      python += "from rgb_arr import rgb_array\n\n";
    else
      python += "from ti_hub import rgb_array\n\n";

    python += 'def draw_rgbarr_image(rgbarr, img):\n';
    python += '  for i in range(len(img) // 4):\n';
    python += '    rgbarr.set(img[i * 4], img[i*4 + 1], img[i*4 + 2], img[i*4 + 3])\n';
    python += '\n';

    python += '#your image data\n';
    python += '#' + img.width + 'x' + img.height + ' RGB-888 pixels\n';
    python += 'image = ' + stringToPythonBytes(im, 0) + '\n\n';
    python += '#image drawing code sample\n';
    python += 'rgbarr = rgb_array()\n';
    python += 'draw_rgbarr_image(rgbarr, image)\n';

    let calcName = global_inFileName;
    const iname = calcName.indexOf('.');
    if (iname >= 0) calcName = calcName.substring(0, iname);
    return [python, calcName];
}

// --- handleOutImgPythonMB (index.html:780) -----------------------------------------------------

export function handleOutImgPythonMB(img, img_a) {
    let python = "";
    python += "#image converted on TI-Planet\n#tiplanet.org/img2calc\n\n";

    if (format === 'microbit.py') {
      if (target === '8xpython') {
        python += "from microbit import *\n";
        python += "from mb_disp import display, Image\n";
      }
      else
        python += "from microbit import display, Image\n";
    }
    else if (target === 'cx2')
      python += "from ti_innovator import send\n";
    else if (target === '8xpython')
      python += "from ti_hub import send\n";
    python += '\n';

    if(format==="ti_hub_mb.py") {
      python += "#function to send the micro:bit Python code to run\n";
      python += "def send_microbit(cmd):\n";
      python += '  send("\\x04")\n';
      python += '  send(cmd)\n';
      python += '  send("\\x05")\n';
      python += "\n";
    }

    python += 'def draw_mb_image(img):\n';
    if (format === 'microbit.py') {
      python += '  display.show(Image(img))\n';
    }
    else if(format==="ti_hub_mb.py") {
      python += `  send_microbit('display.show(Image("'+img+'"))')\n`;
    }
    python += '\n';

    python += '#your image data\n';
    python += '#' + img.width + 'x' + img.height + ' 10-shades of gray pixels\n';
    python += 'image = "';
    for(let y=0; y<img.height; y++) {
      for(let x=0; x<img.width; x++) {
        let gray = getPixel_RGBA_array(img_a, y*img.width+x, 8, 8, 8, 1)[1];
        gray = 9 - Math.round(gray * 9 / 255);
        python += gray;
      }
      if (y<img.height-1)
        python += ":";
    }
    python += '"\n\n';
    python += '#image drawing code sample\n';
    python += "draw_mb_image(image)\n";

    let calcName = global_inFileName;
    const iname = calcName.indexOf('.');
    if (iname >= 0) calcName = calcName.substring(0, iname);
    return [python, calcName];
}

// --- handleOutImgPythonRLE (index.html:838) ----------------------------------------------------

export function handleOutImgPythonRLE(img, img_a) {
    const paletteRGBA_a = [];
    let ialpha = -1;
    let r = 5, g = 6, b = 5, a = 1;
    if(format === "hpprime.py") {
        r = 8; g = 8; b = 8;
    }
    for (let i = 0; i < img.height * img.width; i++) {
        const color_rgba = getPixel_RGBA_array(img_a, i, r, g, b, a);
        if (indexOfArrayInArray(color_rgba, paletteRGBA_a) < 0) {
            const icolor = paletteRGBA_a.length;
            paletteRGBA_a.push(color_rgba);
            if (ialpha < 0 && !color_rgba[3]) {
                ialpha = icolor;
            }
        }
    }
    let im = "";
    let c = 0;
    let ic = 0;
    let prec = 0;
    let cour = 0;
    let npal = paletteRGBA_a.length;
    let nbits = 0;
    let tpal = npal - 1;
    while(tpal) {
      tpal >>= 1;
      nbits += 1;
    }
    let maskcol = (1 << nbits) - 1;
    let maskcnt = 0xFF >> nbits >> 1;
    for(let y=0; y<img.height; y++) {
      for(let x=0; x<img.width; x++) {
        cour = indexOfArrayInArray(getPixel_RGBA_array(img_a, y*img.width+x, r, g, b, a), paletteRGBA_a);
        if(x==0 && y==0) {
          prec=cour;
          c=0;
        }
        if(prec == cour) c += 1;
        if(prec != cour || y+1==img.height && x+1==img.width) {
          while(c > 0)
          {
            let tc = c;
            let vim = prec;
            let scnt = '';
            if(tc <= maskcnt) {
              ic = tc;
              c -= ic;
              ic -= 1;
              vim |= ic << nbits;
            }
            else {
              ic = Math.min(tc, (1<<(15-nbits+(nbits==8)))-1);
              c -= ic;
              ic -= 1;
              vim |= (((ic & maskcnt) << nbits) | (1<<(7+(nbits==8)))) & 0xFF;
              scnt = String.fromCharCode(ic >> (7-nbits+(nbits==8)));
            }
            im += String.fromCharCode(vim);
            im += scnt;
          }
          if(prec != cour) {
            c = 1;
            prec = cour;
            if(y+1==img.height && x+1==img.width) {
              im += String.fromCharCode(cour);
              if (nbits==8) im += String.fromCharCode(0);
            }
          }
        }
      }
    }
    let python = "";
    python += "#image converted on TI-Planet\n#tiplanet.org/img2calc\n\n";
    switch(format) {
        case "ti_graphics.py":
            python += "from ti_graphics import fillRect, setColor\n";
            break;
        case "ti_draw_cx.py":
        case "ti_draw_ce.py":
            python += "from ti_draw import fill_rect, set_color\n";
            break;
        case "casioplot_cg.py":
        case "casioplot_g3.py":
            python += "from casioplot import set_pixel\n\n";
            break;
        case "hpprime.py":
            python += "from hpprime import fillrect\n";
            break;
        case "graphic.py":
        case "graphic_ns.py":
        case "graphic_g3.py":
        case "graphic_cg.py":
            python += "from graphic import fill_rect\n";
            break;
        case "gint_g3.py":
        case "gint_cg.py":
            python += "from gint import drect\n";
            break;
        case "kandinsky.py":
        case "kandinsky_cg.py":
            python += "from kandinsky import fill_rect\n";
            break;
    }
    python +=        '\n#the image drawing function\n';
    if (format === 'hpprime.py' || format === 'nsp_cx.py' || format === 'nsp_ns.py')
      python +=      '#- layer to draw on\n';
    python +=        '#- rle : image RLE-compressed data\n';
    python +=        '#- w : width of image\n';
    python +=        '#- pal : palette of colors to use with image\n';
    python +=        '#- zoomx : horizontal zoom\n';
    python +=        '#- zoomy : vertical zoom\n';
    python +=        '#- itransp : index of 1 transparent color in palette or -1 if none\n';
    if (format === 'hpprime.py' || format === 'nsp_cx.py' || format === 'nsp_ns.py')
      python +=      'def draw_image(layer, rle, x0, y0, w, pal, zoomx=1, zoomy=1, itransp=-1):\n';
    else
      python +=      'def draw_image(rle, x0, y0, w, pal, zoomx=1, zoomy=1, itransp=-1):\n';
    python +=        '  i, x = 0, 0\n';
    python +=        '  x0, y0 = int(x0), int(y0)\n';
    python +=        '  nvals = len(pal)\n';
    python +=        '  nbits = 0\n';
    python +=        '  nvals -= 1\n';
    python +=        '  while(nvals):\n';
    python +=        '    nvals >>= 1\n';
    python +=        '    nbits += 1\n';
    python +=        '  maskval = (1 << nbits) - 1\n';
    python +=        '  maskcnt = (0xFF >> nbits >> 1) << nbits\n';
    python +=        '  while i<len(rle):\n';
    python +=        '    v = rle[i]\n';
    python +=        '    mv = v & maskval\n';
    python +=        '    c = (v & maskcnt) >> nbits\n';
    python +=        '    if (v & 0b10000000 or nbits == 8):\n';
    python +=        '      i += 1\n';
    python +=        '      c |= rle[i] << (7 - nbits + (nbits == 8))\n';
    python +=        '    c = c + 1\n';
    python +=        '    while c:\n';
    python +=        '      cw = min(c, w - x)\n';
    python +=        '      if mv != itransp:\n';
    if (format === 'ti_draw_cx.py') {
      python +=      '        set_color(pal[mv])\n';
      python +=      '        fill_rect(x0 + x*zoomx, y0, cw*zoomx, zoomy)\n';
    }
    if (format === 'ti_draw_ce.py') {
      python +=      '        set_color(*pal[mv])\n';
      if (target === '8xonline')
        python +=    '        fill_rect(x0 + x*zoomx, y0, cw*zoomx, zoomy)\n';
      else
        python +=    '        fill_rect(x0 + x*zoomx - 1, y0 - 1, cw*zoomx + 1, zoomy + 1)\n';
    }
    else if (format === 'ti_graphics.py') {
      python +=      '        setColor(pal[mv])\n';
      python +=      '        fillRect(x0 + x*zoomx, y0, cw*zoomx, zoomy)\n';
    }
    else if (format === 'casioplot_cg.py' || format === 'casioplot_g3.py') {
      python +=      '        col = pal[mv]\n';
      python +=      '        for l in range(0, zoomy, zoomy < 0 and -1 or 1):\n';
      python +=      '          for k in range(cw):\n';
      python +=      '            for p in range(0, zoomx, zoomx < 0 and -1 or 1):\n';
      python +=      '              set_pixel(x0 + (x + k)*zoomx + p - (zoomx < 0), y0 + l - (zoomy < 0), col)\n';
    }
    else if (format === 'hpprime.py')
      python +=      '        fillrect(layer, x0 + x*zoomx, y0, cw*zoomx, zoomy, pal[mv], pal[mv])\n';
    else if (format === 'nsp_cx.py' || format === 'nsp_ns.py') {
      python +=      '        col = pal[mv]\n';
      python +=      '        for l in range(0, zoomy, zoomy < 0 and -1 or 1):\n';
      python +=      '          for k in range(cw):\n';
      python +=      '            for p in range(0, zoomx, zoomx < 0 and -1 or 1):\n';
      python +=      '              layer.setPx(x0 + (x + k)*zoomx + p - (zoomx < 0), y0 + l - (zoomy < 0), col)\n';
    }
    else if (format === 'gint_cg.py' || format === 'gint_g3.py')
      python +=      '        drect(x0 + x*zoomx, y0, cw*zoomx, zoomy, pal[mv])\n';
    else
      python +=      '        fill_rect(x0 + x*zoomx, y0, cw*zoomx, zoomy, pal[mv])\n';
    python +=        '      c -= cw\n';
    python +=        '      x = (x + cw) % w\n';
    python +=        '      y0 += x == 0 and zoomy\n';
    python +=        '    i += 1\n\n\n';
    python +=         "#palette for your image\n"
    python +=         "#" + paletteRGBA_a.length+ " " + ((format === 'nsp_cx.py' || format === 'nsp_ns.py') ? "RGB-565" : "RGB-888")+" colors\n";
    python +=         'palette = (\n';
    let python_line = '';
    let color_names = [];
    let color_vals = [];
    if(format === 'kandinsky.py' || format === 'kandinsky_cg.py') {
      color_names = ["w", "k", "gray", "r", "g", "b", "y", "brown", "pink", "orange", "purple", "cyan", "magenta"];
      color_vals = [[255,255,255,255], // white + w
        [0,0,0,255], // black + k
        [0xa7,0xa7,0xa7,255], // gray + grey
        [255,0,0,255], // red + r
        [0x50,0xc1,0x02,255], // green + g
        [0,0,255,255], // blue + b
        [255,255,0,255], // yellow + y
        [0x8d,0x73,0x50,255], // brown
        [0xff,0xab,0xb6,255], // pink
        [0xfe,0x87,0x1f,255], // orange
        [0x6e,0x2d,0x79,255], // purple
        [0,255,255,255,255], // cyan
        [255,5,136], // magenta
      ];
    }
    else if(format === 'graphic.py' || format === 'graphic_ns.py' || format === 'graphic_cg.py' || format === 'graphic_g3.py') {
      color_names = ["", "black", "red", "green", "blue", "yellow", "cyan", "magenta"];
      color_vals = [[255,255,255,255], // white + w + ""
        [0,0,0,255], // black
        [255,0,0,255], // red
        [0x50,0xc1,0x02,255], // green
        [0,0,255,255], // blue
        [255,255,0,255], // yellow
        [0,255,255,255,255], // cyan
        [255,5,136], // magenta
      ];
    }
    for(let k = 0; k < color_vals.length; k++) {
      color_vals[k] = simplPixel_RGBA(color_vals[k], r, g, b, a);
    }
    for(let k = 0; k < paletteRGBA_a.length; k++) {
      let python_val =  ''
      if (format === 'nsp_cx.py' || format === 'nsp_ns.py' || format === 'gint_cg.py' || format === 'gint_g3.py') {
        let col = color2int(paletteRGBA_a[k], 5, 6, 5, 0);
        python_val = col + ',';
      }
      else if (format === 'hpprime.py') {
        let col = color2int(paletteRGBA_a[k], 8, 8, 8, 0);
        python_val = col + ',';
      }
      else {
        python_val = '(' + paletteRGBA_a[k][0] + ',' + paletteRGBA_a[k][1] + ',' + paletteRGBA_a[k][2] + '),';
        let i_color_name = indexOfArrayInArray(paletteRGBA_a[k], color_vals);
        if(i_color_name >= 0) {
          let python_val_alt = '"' + color_names[i_color_name] + '",';
          if(python_val_alt.length <= python_val.length) python_val = python_val_alt;
        }
        if (format === 'graphic.py' || format === 'graphic_ns.py' || format === 'graphic_cg.py' || format === 'graphic_g3.py') {
          let python_val_alt = color2int(paletteRGBA_a[k], 5, 6, 5, 0) + ',';
          if(python_val_alt.length <= python_val.length) python_val = python_val_alt;
        }
        else if (format === 'kandinsky.py' || format === 'kandinsky_cg.py') {
          let hex_l = [];
          for(let i=0; i<3; i++) {
            let v = paletteRGBA_a[k][i];
            let hex = v.toString(16);
            if(v < 0x10) hex = "0" + hex;
            hex_l[i] = hex;
          }
          let python_val_alt = '"#' + hex_l[0].toString(16) + hex_l[1].toString(16) + hex_l[2].toString(16) + '",';
          if(python_val_alt.length <= python_val.length) python_val = python_val_alt;
        }
      }
      // lines up to 256 chars for compatibility with Casio oncalc editor
      if (((format === 'casioplot_cg.py' || format === 'casioplot_g3.py') && (python_line + python_val).length > 256) || ((format === 'graphic_cg.py' || format === 'graphic_g3.py' || format === 'graphic.py' || format === 'graphic_ns.py') && (python_line + python_val).length > 128)) {
        python += python_line + '\n';
        python_line = '';
      }
      python_line += python_val;
    }
    python += python_line + '\n';
    python +=      ')\n\n';
    python +=        '#your image data\n';
    python +=        '#' + img.width + 'x' + img.height + ' RLE-' + nbits + ' pixels\n';
    python +=        'image = (\n';
    if(format === 'casioplot_cg.py' || format === 'casioplot_g3.py')
      python +=      stringToPythonBytes(im, 256);
    else if(format === 'graphic_cg.py' || format === 'graphic_g3.py' || format === 'graphic.py' || format === 'graphic_ns.py')
      python +=      stringToPythonBytes(im, 128);
    else
      python +=      stringToPythonBytes(im, 0);
    python +=        ')\n\n';
    python +=        '#image drawing code sample\n';
    if (format === 'hpprime.py') {
      python +=      'from hpprime import eval\n';
      python +=      'draw_image(0, image, 0, 0, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';
      python +=      'eval("wait()")\n';
    }
    else if (format === 'nsp_cx.py' || format === 'nsp_ns.py') {
      python +=      'from nsp import Texture, waitKeypress\n';
      python +=      'layer = Texture(320, 240, 0)\n';
      python +=      'layer.fill(0)\n';
      python +=      'draw_image(layer, image, 0, 0, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';
      python +=      'layer.display()\n';
      python +=      'waitKeypress()\n';
    }
    else if(format === 'ti_graphics.py') {
      python +=      'from ti_system import disp_wait\n';
      python +=      'draw_image(image, 0, 30, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';
      python +=      'disp_wait()\n';
    }
    else if(format === 'ti_draw_ce.py') {
      python +=      'from ti_draw import show_draw\n';
      python +=      'draw_image(image, 0, 30, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';
      python +=      'show_draw()\n';
    }
    else if(format === 'ti_draw_cx.py') {
      python +=      'from ti_draw import use_buffer, paint_buffer\n';
      python +=      'use_buffer()\n';
      python +=      'draw_image(image, 0, 0, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';
      python +=      'paint_buffer()\n';
    }
    else if(format === 'casioplot_cg.py' || format === 'casioplot_g3.py') {
      python +=      'from casioplot import show_screen\n';
      python +=      'draw_image(image, 0, 0, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';
      python +=      'show_screen()\n';
    }
    else
      python +=      'draw_image(image, 0, 0, '+img.width+', palette, zoomx=1, zoomy=1, itransp='+ialpha+')\n';

    let calcName = global_inFileName;
    const iname = calcName.indexOf('.');
    if (iname >= 0) calcName = calcName.substring(0, iname);
    return [python, calcName];
}

// --- handleOutImgForTIZ80 (index.html:1149) ----------------------------------------------------

export function handleOutImgForTIZ80(img, img_a) {
    let data = [];
    switch (format)
    {
        case "im8c.8xv": {
            const paletteRGBA_a = [];
            let ialpha = -1;
            const r = 5, g = 6, b = 5, a = 1;
            for (let i = 0; i < img.height * img.width; i++) {
                const color = getPixel_RGBA_int(img_a, i, r, g, b, a);
                if (paletteRGBA_a.indexOf(color) < 0) {
                    const icolor = paletteRGBA_a.length;
                    paletteRGBA_a.push(color);
                    if (ialpha < 0 && !(color >> (r + g + b))) {
                        ialpha = icolor;
                    }
                }
            }
            const paletteHeader_a = [];
            if (ialpha < 0) {
                paletteHeader_a.push(1, 0, 0);
            } else {
                paletteHeader_a.push(1, 1, ialpha & 0xFF);
            }
            paletteHeader_a.push(paletteRGBA_a.length & 0xFF);

            const paletteRGB_a = [];
            for (let i = 0; i < paletteRGBA_a.length; i++) {
                const color = paletteRGBA_a[i] & 0xFFFF;
                paletteRGB_a.push(color & 0xFF, color >> 8);
            }
            const dataRLE_a = [];
            let pixBuffer_a = [];
            for (let i = 0; i < img.height * img.width; i++) {
                const curcolor = getPixel_RGBA_int(img_a, i, r, g, b, a);
                const icolor = paletteRGBA_a.indexOf(curcolor);
                let ncurcolor = 1;
                while (ncurcolor < 128 && i < img.height * img.width - 1 && curcolor === getPixel_RGBA_int(img_a, i + 1, r, g, b, a)) {
                    ncurcolor++;
                    i++;
                }
                if (ncurcolor > 1) {
                    while (pixBuffer_a.length) {
                        const n = Math.min(pixBuffer_a.length, 128);
                        dataRLE_a.push(n - 1);
                        for (let j = 0; j < n; j++) {
                            dataRLE_a.push(pixBuffer_a[j]);
                        }
                        pixBuffer_a = pixBuffer_a.slice(n);
                    }
                    dataRLE_a.push(0x80 + ncurcolor - 2, icolor);
                } else {
                    pixBuffer_a.push(icolor);
                }
            }
            // No trailing flush of pixBuffer_a here: the reference drops literal pixels that are
            // still buffered when the image ends. Reproduced as-is — this is about matching
            // img2calc byte for byte, not about fixing it.
            data = [].concat(
                str2charCodeArray("IM8C"),
                int2LittleEndianArray(img.width, 3),
                int2LittleEndianArray(img.height, 3),
                paletteHeader_a,
                paletteRGB_a,
                dataRLE_a,
            );
            break;
        }

        case "8ca": {
            const r = 5, g = 6, b = 5, a = 0;
            data.push(0x81);
            for (let i = 0; i < img.height * img.width; i++) {
                const color = getPixel_RGBA_int(img_a, reqWidth * (reqHeight - Math.floor(i / reqWidth) - 1) + (i % reqWidth), r, g, b, a);
                data.push(color & 0xFF, color >> 8);
            }
            break;
        }

        case "8ci": {
            const paletteRGBA_a = [];
            for (let i = 0; i < global_paletteArray.length / 4; i++) {
                const color = getPixel_raw(global_paletteArray, i);
                paletteRGBA_a.push(color);
            }
            for (let i = 0; i < img.height * img.width; i += 2) {
                let icolor = 0;
                for (let j = 0; j < 2; j++) {
                    const color = getPixel_raw(img_a, i + j);
                    icolor = (icolor << 4) | nearestiRGB(color, paletteRGBA_a);
                }
                data.push(icolor);
            }
            break;
        }

        case "8xi":
        case "83i":
        case "86i":
        case "85i":
        case "82i":
        case "73i": {
            for (let i = 0; i < img.height * img.width; i += 8) {
                let icolor = 0;
                for (let j = 0; j < 8; j++) {
                    const color = getPixel_RGBA_int(img_a, i + j, 0, 0, 0, 1);
                    icolor = (icolor << 1) | color;
                }
                data.push(icolor);
            }
            break;
        }

        default: {
            throw new Error("Unsupported conversion type");
        }
    }

    data = [].concat(
        int2LittleEndianArray(data.length, 2),
        data
    );

    let name = "";
    let calcName = "";
    if (format === "im8c.8xv" || format === "86i" || format === "85i") {
        name = oncalcName;
        name = name.substring(0, 1).toUpperCase() + name.substring(1, 8);
        calcName = name;
    } else if (format === "8ca" || format === "8ci" || format === "8xi" || format === "83i" || format === "73i" || format === "82i" || format === "85i" || format === "86i") {
        let num = oncalcNum;
        calcName = `${(format === "8ca") ? "Image" : "Pic"}${num}`;
        if (num === 0) num = 10;
        name = String.fromCharCode((format === "8ca") ? 0x3C : 0x60, num - ((format === "73i") ? 0 : 1));
    }
    const nameArray = str2charCodeArray(padStr(name, 8, String.fromCharCode((format === "85i" || format === "86i") ? 0x20 : 0x00)));
    const size2 = data.length;
    data = [].concat(
        int2LittleEndianArray(size2, 2),
        data,
    );
    if (format === "im8c.8xv" || format === "8ca" || format === "8ci" || format === "8xi")
        data = [].concat(
            [(format === "8ca" || format === "8ci") ? 0x0A : 0x00, (format === "8xi") ? 0x00 : 0x80],
            data,
        );
    data = [].concat(
        nameArray,
        data,
    );
    if (format === "85i" || format === "86i") {
        data = [].concat(
            calcName.length,
            data,
        );
    }
    data = [].concat(
        int2LittleEndianArray(size2, 2),
        [(format === "im8c.8xv") ? 0x15 : (format === "8ca") ? 0x1A : (format === "86i" || format === "85i") ? 0x11 : 0x07],
        data,
    );
    data = [].concat(
        int2LittleEndianArray(data.length - size2 - 2, 2),
        data,
    );
    data = [].concat(
        str2charCodeArray((format === "82i") ? "**TI82**" : (format === "85i") ? "**TI85**" : (format === "86i") ? "**TI86**" : (format === "73i") ? "**TI73**" : (format === "83i") ? "**TI83**" : "**TI83F*"),
        [0x1A, (format === "85i") ? 0x0C : 0x0A, (format === "im8c.8xv") ? 0x0A : (format === "8ci") ? 0x0F : (format === "8xi") ? 0x0B : 0x00],
        str2charCodeArray(padStr("Created on TI-Planet.org by img2calc", 42, String.fromCharCode(0))),
        int2LittleEndianArray(data.length, 2),
        data,
        int2LittleEndianArray(crcti8x(data), 2),
    );

    return [data, calcName];
}

// --- handleOutImgForZero (index.html:1346) -----------------------------------------------------

export function handleOutImgForZero(img, img_a) {
    let data = [];
    switch (format)
    {
        case "zpic": {
            const r = 5, g = 6, b = 5, a = 0;
            data.push(0x32, 0, 0, 0, 0, 0, 0, 0);
            let x = 0;
            let y = 22;
            let n = 0;
            for (let i = 0; i < img.height * img.width; i++) {
                if (getPixel_raw(img_a, i)[3] >= 255) {
                    n++;
                    const color = getPixel_RGBA_int(img_a, i, r, g, b, a);
                    data.push(0x0E, 0, 0, 0, x & 0xFF, x >> 8, y & 0xFF, y >> 8, color & 0xFF, color >> 8);
                }
                x = (x + 1) % reqWidth;
                if (x==0) y++;
            }
            data[4] = n & 0xFF;
            data[5] = (n >> 8) & 0xFF;
            data[6] = (n >> 16) & 0xFF;
            data[7] = (n >> 24) & 0xFF;
            break;
        }

        default: {
            throw new Error("Unsupported conversion type");
        }
    }

    let calcName = `pic${oncalcNum}`;
    return [data, calcName];
}

// --- handleOutImgCP (index.html:1401) ----------------------------------------------------------

export function handleOutImgCP(img, img_a) {
    let data = [];
    switch (format) {
        case "c2p":
        case "cp.g3p":
        case "cp01.g3p":
        case "cp01.g4p": {
            const r = 5, g = 6, b = 5, a = 0;
            for (let i = 0; i < img.height * img.width; i++) {
                const color = getPixel_RGBA_int(img_a, i, r, g, b, a);
                data.push(color >> 8, color & 0xFF);
            }
            break;
        }
        case "i.c2p":
        case "cp_i.g3p":
        case "cp01_i.g3p":
        case "cp01_i.g4p": {
            const paletteRGBA_a = [];
            for (let i = 0; i < global_paletteArray.length / 4; i++) {
                const color = getPixel_raw(global_paletteArray, i);
                paletteRGBA_a.push(color);
            }
            for (let i = 0; i < img.height * img.width; i += 2) {
                let icolor = 0;
                for (let j = 0; j < 2; j++) {
                    const color = getPixel_raw(img_a, i + j);
                    icolor = (icolor << 4) | nearestiRGB(color, paletteRGBA_a);
                }
                data.push(icolor);
            }
            break;
        }
        default:
            throw new Error("Unsupported conversion type");
    }

    data = Array.from(deflate_f(new Uint8Array(data),{}));

    let footer = [];

    if (format === "cp01.g3p" || format === "cp01_i.g3p" || format === "cp01.g4p" || format === "cp01_i.g4p")
        footer = [].concat(
            str2charCodeArray("0100", 7),
            int2LittleEndianArray(0x30066084, 13),
            int2LittleEndianArray(0x300610, 12),
            int2LittleEndianArray(0x0110, 12),
            int2LittleEndianArray(0x33338309, 12),
            int2LittleEndianArray(0x100360, 12),
            int2LittleEndianArray(0x100310, 12),
            int2LittleEndianArray(0x0110, 24),
            int2LittleEndianArray(0x31280610, 4),
            int2LittleEndianArray(0x85, 8),
            int2LittleEndianArray(0x32288609, 12),
            int2LittleEndianArray(0x42778609, 12)
        );
    else if (format === "c2p" || format === "i.c2p") {
        let footer2 = [].concat(
            int2LittleEndianArray(3, 10),
            int2LittleEndianArray(0x60, 2),
            int2LittleEndianArray(3, 10),
            int2LittleEndianArray(0x10, 2),
            int2LittleEndianArray(1, 10),
            int2LittleEndianArray(0x10, 2),
            int2LittleEndianArray(0x5002, 10),
            int2LittleEndianArray(0x110, 2)
        );

        footer2 = [].concat(
            footer2,
            footer2,
            footer2
        );
        footer2 = [].concat(
            str2charCodeArray("0100"),
            int2BigEndianArray(footer2.length, 4),
            footer2
        );
        let footer1 = [].concat(
            int2LittleEndianArray(0, 8),
            int2LittleEndianArray(0x10, 2),
            int2LittleEndianArray(1, 10),
            int2LittleEndianArray(0x10, 2),
            int2LittleEndianArray(5, 10),
            int2LittleEndianArray(0x9809, 2)
        );

        footer1 = [].concat(
            str2charCodeArray("0100"),
            int2LittleEndianArray(0, 3),
            int2LittleEndianArray(0x70078C, 11),
            int2LittleEndianArray(0x60, 2),
            int2LittleEndianArray(0x7007, 2),
            footer1,
            int2LittleEndianArray(0x6004, 10),
            int2LittleEndianArray(0x60, 2),
            int2LittleEndianArray(0x6004, 2),
            footer1,
            int2LittleEndianArray(0, 12),
            int2LittleEndianArray(0x85312806, 10),
            int2LittleEndianArray(0x10, 2),
            int2LittleEndianArray(0x322806, 10),
            int2LittleEndianArray(0x9809, 4),
            new Array(6).fill(0xFF),
        );
        let pi = 0x93151403;

        footer = [].concat(
            footer1,
            footer2,
            int2LittleEndianArray(2, 10),
            int2LittleEndianArray(0x070110, 12),
            int2LittleEndianArray(0x0110, 2),
            int2LittleEndianArray(pi, 4),
            int2LittleEndianArray(0, 6),
            int2LittleEndianArray(0x60, 2),
            int2LittleEndianArray(pi,10),
            int2LittleEndianArray(0x10, 2),
            int2LittleEndianArray(pi, 10),
            int2LittleEndianArray(0x60, 2),
            int2LittleEndianArray(pi, 10),
            int2LittleEndianArray(0x10, 2),
            new Array(3).fill(1),
            new Array(5).fill(0xFF)
        );

    }

    if (format === "cp.g3p" || format === "cp01.g3p" || format === "cp01.g4p" || format === "cp_i.g3p" || format === "cp01_i.g3p" || format === "cp01_i.g4p")
        data = [].concat(
            data,
            Array.from(adler32(1,new Uint8Array(data),data.length,0))
        );

    if (format === "cp.g3p" || format === "cp_i.g3p") {
        data = binaryInvertArray(data);
        data = binarySwapArray(data,5,3);
    }

    data = [].concat(
        int2BigEndianArray(data.length, 4),
        data
    );

    data = [].concat(
        format === "cp.g3p" || format === "cp_i.g3p" ? int2LittleEndianArray(0x100, 4) : str2charCodeArray("0100"),
        int2BigEndianArray(data.length, 4),
        int2BigEndianArray(img.width, 4),
        int2BigEndianArray(img.height, 2),
        int2BigEndianArray((format === "c2p" || format === "cp.g3p" || format === "cp01.g3p" || format === "cp01.g4p") ? 0x10 : 0x03, 2),
        format === "cp.g3p" || format === "cp01.g3p" || format === "cp01.g4p" || format === "cp_i.g3p" || format === "cp01_i.g3p" || format === "cp01_i.g4p" ? int2LittleEndianArray(1, 4) : str2charCodeArray("\0\xFF\0\xFF\0\xFF\0\xFF\0\x01\0\xFF\xFF\xFF\xFF\xFF"),
        data
    );

    data = [].concat(
        int2BigEndianArray(format == "cp.g3p" || format == "cp_i.g3p" ? 1 : 9, 4),
        int2BigEndianArray(data.length, 4),
        new Array(0x8).fill(0),
        int2BigEndianArray(footer.length, 4),
        new Array(0x70).fill(0),
        data
    );

    let header2 = str2charCodeArray(format === "cp.g3p" || format === "cp_i.g3p" ? "CP\0\x01" : format === "cp01.g4p" || format === "cp01_i.g4p" ? "CP0100Cy875" : format === "cp01.g3p" || format === "cp01_i.g3p" ? "CP0100Ly755" : "CC0100ColorCP",16);

    data = [].concat(
        new Array(format === "c2p" || format === "i.c2p" ? 4 : 2).fill(0),
        header2,
        int2BigEndianArray(header2.length + data.length + footer.length + 4, 4),
        data
    );

    let header1 = format === "c2p" || format === "i.c2p" ? str2charCodeArray("CASIO\0\0\0c2p\0\0\0\0\0\0\x01\0\x10\0\x01\0") : str2charCodeArray("USBPower\x7D\0\x10\0\x10\0");

    header2 = new Array(format === "c2p" || format === "i.c2p" ? 1 : 7).fill(0);

    let size = header1.length + header2.length + data.length + footer.length + 4 + (format === "cp.g3p" || format === "cp01.g3p" || format === "cp01.g4p" || format === "cp_i.g3p" || format === "cp01_i.g3p" || format === "cp01_i.g4p" ? 5 : 0);
    let sizebytes = int2BigEndianArray(size, 4);

    if (format === "c2p" || format === "i.c2p")
        header1 = [].concat(
            header1,
            int2BigEndianArray(size, 3),
            [(0x1D1 - (size & 0xFF)) & 0xFF],
        );
    else
        header1 = [].concat(
            header1,
            (sizebytes[3]+0x41)%0x100,
            1,
            sizebytes,
            (sizebytes[3]+0xB8)%0x100,
        );
    header1 = binaryInvertArray(header1);
    let header = [].concat(
        header1,
        header2
    );
    if (format === "cp.g3p" || format === "cp01.g3p" || format === "cp01.g4p" || format === "cp_i.g3p" || format === "cp01_i.g3p" || format === "cp01_i.g4p")
        header = [].concat(
            header,
            (format == "cp.g3p" || format == "cp_i.g3p" ? sizebytes[2] + sizebytes[3] + 0xAB : -sizebytes[2] - sizebytes[3] + 7)%0x100,
            (format == "cp.g3p" || format == "cp_i.g3p" ? sizebytes[3] + 0x98 : -sizebytes[3] + 0x16)%0x100,
        );
    data = [].concat(
        header,
        data,
        footer
    );

    let name = oncalcName;
    name = name.substring(0, 8);
    let calcName = name;

    return [data, calcName];
}

// --- dispatcher (index.html:1630) --------------------------------------------------------------

const TIZ80_FORMATS = ['im8c.8xv', '8ca', '8ci', '8xi', '83i', '73i', '82i', '85i', '86i'];
const CP_FORMATS = ['cp.g3p', 'cp01.g3p', 'cp01.g4p', 'c2p', 'cp_i.g3p', 'cp01_i.g3p', 'cp01_i.g4p', 'i.c2p'];
const RLE_FORMATS = ['ti_graphics.py', 'ti_draw_ce.py', 'ti_draw_cx.py', 'graphic.py', 'graphic_cg.py',
  'graphic_g3.py', 'gint_cg.py', 'gint_g3.py', 'graphic_ns.py', 'nsp_cx.py', 'nsp_ns.py',
  'casioplot_cg.py', 'casioplot_g3.py', 'kandinsky.py', 'kandinsky_cg.py', 'hpprime.py'];
const MB_FORMATS = ['microbit.py', 'ti_hub_mb.py'];

/** Run the reference encoder for the configured format. Returns `{ bytes, calcName }`. */
export function encode(img, img_a) {
    let r;
    if (TIZ80_FORMATS.includes(format)) r = handleOutImgForTIZ80(img, img_a);
    else if (CP_FORMATS.includes(format)) r = handleOutImgCP(img, img_a);
    else if (format === 'zpic') r = handleOutImgForZero(img, img_a);
    else if (RLE_FORMATS.includes(format)) r = handleOutImgPythonRLE(img, img_a);
    else if (MB_FORMATS.includes(format)) r = handleOutImgPythonMB(img, img_a);
    else if (format === 'ti_hub_rgbarr.py') r = handleOutImgPythonRGBARR(img, img_a);
    else throw new Error(`Unsupported conversion type: ${format}`);

    const [data, calcName] = r;
    // Scripts come back as a JS string; the reference feeds those to str2charCodeArray in
    // addBlobFileLink, so one byte per char code.
    const bytes = typeof data === 'string'
        ? Uint8Array.from(str2charCodeArray(data))
        : Uint8Array.from(data);
    return { bytes, calcName };
}

export const ALL_FORMATS = [...TIZ80_FORMATS, ...CP_FORMATS, 'zpic', ...RLE_FORMATS, ...MB_FORMATS, 'ti_hub_rgbarr.py'];
