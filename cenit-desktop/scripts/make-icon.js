// Genera build/icon.ico (16 a 256 px) desde la estrella del logo de Cenit, sin dependencias:
// la misma figura que assets/cenit-star.svg y js/brand.js del frontend
// (M12 1Q14.2 10.8 20 13Q14.2 15.2 12 23Q9.8 15.2 4 13Q9.8 10.8 12 1Z), con el degradado del tema
// oscuro (#D95F8E -> #A78BFA) sobre una baldosa redondeada del fondo oscuro de la app (#0B1020).
// Uso: node scripts/make-icon.js   (el .ico resultante se versiona; solo hace falta rehacerlo si cambia el logo)
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const SIZES = [16, 24, 32, 48, 64, 128, 256];
const SAMPLES = 4; // 4x4 muestras por píxel (antialias)
const FROM = [0xd9, 0x5f, 0x8e];
const TO = [0xa7, 0x8b, 0xfa];
const TILE_TOP = [0x1a, 0x16, 0x3a];
const TILE_BOTTOM = [0x0b, 0x10, 0x20];

/** La estrella en coordenadas 0..24, aplanada a un polígono. */
function starPolygon() {
  const segments = [
    [[12, 1], [14.2, 10.8], [20, 13]],
    [[20, 13], [14.2, 15.2], [12, 23]],
    [[12, 23], [9.8, 15.2], [4, 13]],
    [[4, 13], [9.8, 10.8], [12, 1]],
  ];
  const points = [];
  for (const [p0, c, p1] of segments) {
    for (let i = 0; i < 48; i++) {
      const t = i / 48;
      const u = 1 - t;
      points.push([u * u * p0[0] + 2 * u * t * c[0] + t * t * p1[0], u * u * p0[1] + 2 * u * t * c[1] + t * t * p1[1]]);
    }
  }
  return points;
}

function inside(poly, x, y) {
  let hit = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i];
    const [xj, yj] = poly[j];
    if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) hit = !hit;
  }
  return hit;
}

function insideRoundedRect(x, y, size, radius) {
  const cx = Math.min(Math.max(x, radius), size - radius);
  const cy = Math.min(Math.max(y, radius), size - radius);
  return (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2;
}

const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);

function render(size) {
  const star = starPolygon();
  // La estrella ocupa ~80 % del alto de la baldosa, centrada.
  const scale = (size * 0.8) / 22;
  const offsetX = size / 2 - 12 * scale;
  const offsetY = size / 2 - 12 * scale;
  const radius = size * 0.22;
  const rgba = Buffer.alloc(size * size * 4);
  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      let tile = 0;
      let starHits = 0;
      for (let sy = 0; sy < SAMPLES; sy++) {
        for (let sx = 0; sx < SAMPLES; sx++) {
          const x = px + (sx + 0.5) / SAMPLES;
          const y = py + (sy + 0.5) / SAMPLES;
          if (!insideRoundedRect(x, y, size, radius)) continue;
          tile++;
          if (inside(star, (x - offsetX) / scale, (y - offsetY) / scale)) starHits++;
        }
      }
      const total = SAMPLES * SAMPLES;
      const tileAlpha = tile / total;
      const starAlpha = tile ? starHits / tile : 0;
      const bg = mix(TILE_TOP, TILE_BOTTOM, py / size);
      const fg = mix(FROM, TO, (px + py) / (2 * size)); // degradado diagonal, como el SVG
      const color = mix(bg, fg, starAlpha);
      const o = (py * size + px) * 4;
      rgba[o] = Math.round(color[0]);
      rgba[o + 1] = Math.round(color[1]);
      rgba[o + 2] = Math.round(color[2]);
      rgba[o + 3] = Math.round(tileAlpha * 255);
    }
  }
  return rgba;
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function png(size, rgba) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // bits por canal
  header[9] = 6; // RGBA
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0; // sin filtro
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function ico(images) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2); // icono
  header.writeUInt16LE(images.length, 4);
  let offset = 6 + 16 * images.length;
  const entries = images.map(({ size, data }) => {
    const e = Buffer.alloc(16);
    e[0] = size >= 256 ? 0 : size;
    e[1] = size >= 256 ? 0 : size;
    e.writeUInt16LE(1, 4); // planos
    e.writeUInt16LE(32, 6); // bits por píxel
    e.writeUInt32LE(data.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += data.length;
    return e;
  });
  return Buffer.concat([header, ...entries, ...images.map((i) => i.data)]);
}

const outDir = path.join(__dirname, '..', 'build');
fs.mkdirSync(outDir, { recursive: true });
const images = SIZES.map((size) => ({ size, data: png(size, render(size)) }));
fs.writeFileSync(path.join(outDir, 'icon.ico'), ico(images));
fs.writeFileSync(path.join(outDir, 'icon.png'), images[images.length - 1].data);
console.log(`build/icon.ico (${SIZES.join(', ')} px) y build/icon.png (256 px)`);
