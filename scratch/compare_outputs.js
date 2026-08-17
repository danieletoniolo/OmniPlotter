const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

// === Helper functions from tmp/index.html & adler32.js ===

const adler32 = (adler, buf, len, pos) => {
  let s1 = (adler & 0xffff) | 0,
      s2 = ((adler >>> 16) & 0xffff) | 0,
      n = 0;

  while (len !== 0) {
    n = len > 2000 ? 2000 : len;
    len -= n;
    do {
      s1 = (s1 + buf[pos++]) | 0;
      s2 = (s2 + s1) | 0;
    } while (--n);
    s1 %= 65521;
    s2 %= 65521;
  }
  return (s1 | (s2 << 16)) | 0;
};

// Node zlib equivalent of deflate_f
function deflate_f(input, options) {
  // standard zlib deflate (non-raw) unless options.raw is set
  if (options && options.raw) {
    return zlib.deflateRawSync(input);
  }
  return zlib.deflateSync(input);
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

function str2charCodeArray(str, n = 0) {
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

function binarySwapArray(arr, n1, n2) {
    let mask1 = (1 << n1) - 1;
    let mask2 = ((1 << n2) - 1) << n1;
    for(let i = 0; i < arr.length; i++)
        arr[i] = ((arr[i] & mask1) << n2) | ((arr[i] & mask2) >> n1);
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

function color2int(col, r, g, b, a) {
    return col[2] >> (8 - r) | col[1] >> (8 - g) << r | col[0] >> (8 - b) << (r + g) | col[3] >> (8 - a) << (r + g + b);
}

function stringToPythonBytes(s, lmax) {
  let n = s.length;
  let sout = "";
  let lout = "";
  let ng1 = 0;
  let ng2 = 0;
  for(let i=0; i<n; i++) {
    let v = s.charCodeAt(i);
    if(v == 34) ng2++; 
    else if(v == 39) ng1++;
  }
  for(let i=0; i<n; i++) {
    let v = s.charCodeAt(i);
    let nextv = -1;
    if(i < n - 1) nextv = s.charCodeAt(i + 1);
    let cout = "";
    if(v == 7) cout = "\\a";
    else if(v == 8) cout = "\\b";
    else if(v == 9) cout = "\\t";
    else if(v == 10) cout = "\\n";
    else if(v == 11) cout = "\\v";
    else if(v == 12) cout = "\\f";
    else if(v == 13) cout = "\\r";
    else if(v == 92) cout = "\\\\";
    else if(v == 34 && ng2 <= ng1) cout = '\\"';
    else if(v == 39 && ng2 > ng1) cout = "\\'";
    else if(v >= 32 && v <= 0x7E) cout = String.fromCharCode(v);
    else if(v <= 0o77 && (nextv < 48 || nextv > 55)) cout = "\\" + v.toString(8);
    else {
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

// === Real Palette Arrays loaded from Java ===
const mock_pal8ci = JSON.parse(fs.readFileSync(path.join(__dirname, '../target/pal8ci.json'), 'utf8'));
const mock_palcp = JSON.parse(fs.readFileSync(path.join(__dirname, '../target/palcp.json'), 'utf8'));

let global_paletteArray = [];

// === Encoders ===

function handleOutImgForTIZ80(img, img_a, format, target, calcName) {
    let data = [];
    let name = calcName;
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
                const color = getPixel_RGBA_int(img_a, img.width * (img.height - Math.floor(i / img.width) - 1) + (i % img.width), r, g, b, a);
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
    }
    
    data = [].concat(
        int2LittleEndianArray(data.length, 2),
        data
    );

    let num = 1; // mock selection
    if (format === "im8c.8xv" || format === "86i" || format === "85i") {
        name = name.substring(0, 1).toUpperCase() + name.substring(1, 8);
        calcName = name;
    } else {
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

function handleOutImgCP(img, img_a, format, target, calcName) {
    let data = [];
    switch (format) {
        case "c2p":
        case "cp.g3p":
        case "cp01.g3p":
        case "cp01.g4p":
            const r = 5, g = 6, b = 5, a = 0;
            for (let i = 0; i < img.height * img.width; i++) {
                const color = getPixel_RGBA_int(img_a, i, r, g, b, a);
                data.push(color >> 8, color & 0xFF);
            }
            break;
        case "i.c2p":
        case "cp_i.g3p":
        case "cp01_i.g3p":
        case "cp01_i.g4p":
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

    data = Array.from(deflate_f(new Uint8Array(data), {}));

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
            int2LittleEndianArray(pi, 10),
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
            Array.from(adler32(1, new Uint8Array(data), data.length, 0))
        );

    if (format === "cp.g3p" || format === "cp_i.g3p") {
        data = binaryInvertArray(data);
        data = binarySwapArray(data, 5, 3);
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

    let header2 = str2charCodeArray(format === "cp.g3p" || format === "cp_i.g3p" ? "CP\0\x01" : format === "cp01.g4p" || format === "cp01_i.g4p" ? "CP0100Cy875" : format === "cp01.g3p" || format === "cp01_i.g3p" ? "CP0100Ly755" : "CC0100ColorCP", 16);

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
            (sizebytes[3] + 0x41) % 0x100,
            1,
            sizebytes,
            (sizebytes[3] + 0xB8) % 0x100,
        );
    header1 = binaryInvertArray(header1);
    let header = [].concat(
        header1,
        header2
    );
    if (format === "cp.g3p" || format === "cp01.g3p" || format === "cp01.g4p" || format === "cp_i.g3p" || format === "cp01_i.g3p" || format === "cp01_i.g4p")
        header = [].concat(
            header,
            (format == "cp.g3p" || format == "cp_i.g3p" ? sizebytes[2] + sizebytes[3] + 0xAB : -sizebytes[2] - sizebytes[3] + 7) % 0x100,
            (format == "cp.g3p" || format == "cp_i.g3p" ? sizebytes[3] + 0x98 : -sizebytes[3] + 0x16) % 0x100,
        );
    data = [].concat(
        header,
        data,
        footer
    );

    return [data, calcName];
}

function handleOutImgForZero(img, img_a, format, target, calcName) {
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
                x = (x + 1) % img.width;
                if (x==0) y++;
            }
            data[4] = n & 0xFF;
            data[5] = (n >> 8) & 0xFF;
            data[6] = (n >> 16) & 0xFF;
            data[7] = (n >> 24) & 0xFF;
            break;
        }
    }
    
    let num = 1;
    calcName = `pic${num}`;
    return [data, calcName];
}

// === Run Encoders on Mock Image ===

// We'll create a 12x8 mock image
const W = 12;
const H = 8;
const mock_rgba = new Uint8Array(W * H * 4);
for(let y=0; y<H; y++) {
  for(let x=0; x<W; x++) {
    const idx = (y*W + x)*4;
    // diagonal pattern
    if (x === y) {
      // Red
      mock_rgba[idx] = 255;
      mock_rgba[idx+1] = 0;
      mock_rgba[idx+2] = 0;
      mock_rgba[idx+3] = 255;
    } else if (x === y + 1) {
      // Green
      mock_rgba[idx] = 0;
      mock_rgba[idx+1] = 255;
      mock_rgba[idx+2] = 0;
      mock_rgba[idx+3] = 255;
    } else {
      // Blue
      mock_rgba[idx] = 0;
      mock_rgba[idx+1] = 0;
      mock_rgba[idx+2] = 255;
      mock_rgba[idx+3] = 255;
    }
  }
}

const mockImg = { width: W, height: H };

// Dump pixel data to a binary file so Java can load it and generate the same inputs
fs.writeFileSync('target/mock_pixels.bin', Buffer.from(mock_rgba));

// Generate references
global_paletteArray = new Uint8Array(mock_pal8ci);
const [res_8ci, name_8ci] = handleOutImgForTIZ80(mockImg, mock_rgba, "8ci", "8xcolor", "TESTVAR");
fs.writeFileSync('target/ref_8ci.bin', Buffer.from(res_8ci));

const [res_8ca, name_8ca] = handleOutImgForTIZ80(mockImg, mock_rgba, "8ca", "8xcolor", "TESTVAR");
fs.writeFileSync('target/ref_8ca.bin', Buffer.from(res_8ca));

const [res_im8c, name_im8c] = handleOutImgForTIZ80(mockImg, mock_rgba, "im8c.8xv", "8xpython", "TESTVAR");
fs.writeFileSync('target/ref_im8c.bin', Buffer.from(res_im8c));

const [res_8xi, name_8xi] = handleOutImgForTIZ80(mockImg, mock_rgba, "8xi", "8xp", "TESTVAR");
fs.writeFileSync('target/ref_8xi.bin', Buffer.from(res_8xi));

global_paletteArray = new Uint8Array(mock_palcp);
const [res_g3p, name_g3p] = handleOutImgCP(mockImg, mock_rgba, "cp.g3p", "cg", "TESTVAR");
fs.writeFileSync('target/ref_g3p.bin', Buffer.from(res_g3p));

const [res_cp01_g3p, name_cp01_g3p] = handleOutImgCP(mockImg, mock_rgba, "cp01.g3p", "cg", "TESTVAR");
fs.writeFileSync('target/ref_cp01_g3p.bin', Buffer.from(res_cp01_g3p));

const [res_c2p, name_c2p] = handleOutImgCP(mockImg, mock_rgba, "c2p", "cp2", "TESTVAR");
fs.writeFileSync('target/ref_c2p.bin', Buffer.from(res_c2p));

const [res_zpic, name_zpic] = handleOutImgForZero(mockImg, mock_rgba, "zpic", "zero", "TESTVAR");
fs.writeFileSync('target/ref_zpic.bin', Buffer.from(res_zpic));

console.log("Mock reference files generated successfully in target/");
