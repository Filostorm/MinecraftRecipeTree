import {Zlib} from 'fflate';

const PNG_SIGNATURE = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
const PNG_COLOR_TYPE_RGBA = 6;
const PNG_FILTER_SUB = 1;
const PNG_TILE_MAX_PHYSICAL_SIZE = 8_192;
const PNG_STRIP_MAX_RGBA_BYTES = 64 * 1024 * 1024;
const PNG_MAX_OUTPUT_PIXELS = 192_000_000;
const PNG_MAX_OUTPUT_DIMENSION = 262_144;

const CRC_TABLE = new Uint32Array(256);
for (let entry = 0; entry < CRC_TABLE.length; entry += 1) {
  let value = entry;
  for (let bit = 0; bit < 8; bit += 1) {
    value = (value & 1) === 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  }
  CRC_TABLE[entry] = value >>> 0;
}

function writeUint32(target: Uint8Array, offset: number, value: number): void {
  target[offset] = (value >>> 24) & 0xff;
  target[offset + 1] = (value >>> 16) & 0xff;
  target[offset + 2] = (value >>> 8) & 0xff;
  target[offset + 3] = value & 0xff;
}

function chunkTypeBytes(type: string): Uint8Array {
  if (!/^[A-Za-z]{4}$/.test(type)) {
    throw new Error(`PNG chunk type must contain exactly four ASCII letters; received ${type}.`);
  }
  return new Uint8Array([
    type.charCodeAt(0),
    type.charCodeAt(1),
    type.charCodeAt(2),
    type.charCodeAt(3),
  ]);
}

function pngChunk(type: string, data = new Uint8Array()): Uint8Array {
  const typeBytes = chunkTypeBytes(type);
  const output = new Uint8Array(data.length + 12);
  writeUint32(output, 0, data.length);
  output.set(typeBytes, 4);
  output.set(data, 8);

  let crc = 0xffffffff;
  for (const byte of typeBytes) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  for (const byte of data) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  writeUint32(output, output.length - 4, (crc ^ 0xffffffff) >>> 0);
  return output;
}

export class PngRgbaRowEncoder {
  readonly width: number;
  readonly height: number;
  private readonly parts: Uint8Array[];
  private readonly compressor: Zlib;
  private rows = 0;
  private compressionFinished = false;
  private finished = false;

  constructor(width: number, height: number) {
    if (!Number.isSafeInteger(width) || width <= 0 || !Number.isSafeInteger(height) || height <= 0) {
      throw new Error('PNG dimensions must be positive safe integers.');
    }
    if (width > 0xffffffff || height > 0xffffffff) {
      throw new Error('PNG dimensions exceed the unsigned 32-bit file-format limit.');
    }
    this.width = width;
    this.height = height;

    const header = new Uint8Array(13);
    writeUint32(header, 0, width);
    writeUint32(header, 4, height);
    header[8] = 8;
    header[9] = PNG_COLOR_TYPE_RGBA;
    header[10] = 0;
    header[11] = 0;
    header[12] = 0;
    this.parts = [PNG_SIGNATURE, pngChunk('IHDR', header)];
    this.compressor = new Zlib({level: 6}, (data, final) => {
      if (data.length > 0) this.parts.push(pngChunk('IDAT', data));
      if (final) this.compressionFinished = true;
    });
  }

  pushRow(rgba: Uint8Array | Uint8ClampedArray): void {
    if (this.finished) throw new Error('PNG row encoder is already finished.');
    if (this.rows >= this.height) throw new Error('PNG row encoder received too many rows.');
    if (rgba.length !== this.width * 4) {
      throw new Error(
        `PNG row ${this.rows} contains ${rgba.length} RGBA bytes; expected ${this.width * 4}.`,
      );
    }

    const filtered = new Uint8Array(rgba.length + 1);
    filtered[0] = PNG_FILTER_SUB;
    for (let index = 0; index < rgba.length; index += 1) {
      const left = index >= 4 ? rgba[index - 4] : 0;
      filtered[index + 1] = (rgba[index] - left + 256) & 0xff;
    }

    this.rows += 1;
    this.compressor.push(filtered, this.rows === this.height);
  }

  finish(): Blob {
    if (this.finished) throw new Error('PNG row encoder finish was called more than once.');
    if (this.rows !== this.height) {
      throw new Error(`PNG row encoder received ${this.rows} rows; expected ${this.height}.`);
    }
    if (!this.compressionFinished) {
      throw new Error('PNG zlib stream did not emit its final compressed block.');
    }
    this.finished = true;
    this.parts.push(pngChunk('IEND'));
    return new Blob(this.parts, {type: 'image/png'});
  }
}

export interface TiledPngPlan {
  logicalWidth: number;
  logicalHeight: number;
  scale: number;
  outputWidth: number;
  outputHeight: number;
  outputPixels: number;
  logicalTileWidth: number;
  logicalTileHeight: number;
  columns: number;
  rows: number;
  totalTiles: number;
}

export function planTiledPng(
  logicalWidth: number,
  logicalHeight: number,
  requestedScale = 3,
): TiledPngPlan {
  if (
    !Number.isSafeInteger(logicalWidth) ||
    logicalWidth <= 0 ||
    !Number.isSafeInteger(logicalHeight) ||
    logicalHeight <= 0
  ) {
    throw new Error('Tiled PNG logical dimensions must be positive safe integers.');
  }
  if (!Number.isSafeInteger(requestedScale) || requestedScale <= 0 || requestedScale > 8) {
    throw new Error('Tiled PNG requested scale must be a positive integer no greater than eight.');
  }

  let scale = 0;
  for (let candidate = requestedScale; candidate >= 1; candidate -= 1) {
    const outputWidth = logicalWidth * candidate;
    const outputHeight = logicalHeight * candidate;
    const outputPixels = outputWidth * outputHeight;
    if (
      outputWidth <= PNG_MAX_OUTPUT_DIMENSION &&
      outputHeight <= PNG_MAX_OUTPUT_DIMENSION &&
      outputPixels <= PNG_MAX_OUTPUT_PIXELS
    ) {
      scale = candidate;
      break;
    }
  }
  if (scale === 0) {
    throw new Error(
      'This tree exceeds the tiled PNG safety limit even at 1× resolution. ' +
        'Collapse branches or enable Compact mode before exporting.',
    );
  }

  const outputWidth = logicalWidth * scale;
  const outputHeight = logicalHeight * scale;
  const outputPixels = outputWidth * outputHeight;
  const logicalTileWidth = Math.max(1, Math.floor(PNG_TILE_MAX_PHYSICAL_SIZE / scale));
  const maximumStripPhysicalHeight = Math.max(
    scale,
    Math.floor(PNG_STRIP_MAX_RGBA_BYTES / (outputWidth * 4)),
  );
  const logicalTileHeight = Math.max(
    1,
    Math.floor(Math.min(PNG_TILE_MAX_PHYSICAL_SIZE, maximumStripPhysicalHeight) / scale),
  );
  const columns = Math.ceil(logicalWidth / logicalTileWidth);
  const rows = Math.ceil(logicalHeight / logicalTileHeight);

  return {
    logicalWidth,
    logicalHeight,
    scale,
    outputWidth,
    outputHeight,
    outputPixels,
    logicalTileWidth,
    logicalTileHeight,
    columns,
    rows,
    totalTiles: columns * rows,
  };
}

interface RenderedTile {
  outputX: number;
  width: number;
  height: number;
  rgba: Uint8ClampedArray;
}

export interface RenderTiledPngOptions {
  source: HTMLElement;
  logicalWidth: number;
  logicalHeight: number;
  sourceLeft: number;
  sourceTop: number;
  requestedScale?: number;
  backgroundColor: string;
  onProgress?: (completedTiles: number, totalTiles: number) => void;
}

export interface RenderTiledPngResult {
  blob: Blob;
  plan: TiledPngPlan;
}

function yieldToBrowser(): Promise<void> {
  return new Promise(resolve => globalThis.setTimeout(resolve, 0));
}

export async function renderTiledPng({
  source,
  logicalWidth,
  logicalHeight,
  sourceLeft,
  sourceTop,
  requestedScale = 3,
  backgroundColor,
  onProgress,
}: RenderTiledPngOptions): Promise<RenderTiledPngResult> {
  if (!source.isConnected) {
    throw new Error('The graph export source is no longer attached to the document.');
  }
  const plan = planTiledPng(logicalWidth, logicalHeight, requestedScale);
  const encoder = new PngRgbaRowEncoder(plan.outputWidth, plan.outputHeight);
  const staging = document.createElement('div');
  const clone = source.cloneNode(true) as HTMLElement;
  let completedTiles = 0;

  Object.assign(staging.style, {
    position: 'fixed',
    left: '-100000px',
    top: '0',
    overflow: 'hidden',
    backgroundColor,
  });
  Object.assign(clone.style, {
    position: 'absolute',
    width: '0px',
    height: '0px',
    transform: 'none',
  });
  staging.appendChild(clone);
  document.body.appendChild(staging);

  try {
    await document.fonts?.ready;
    const images = [...clone.querySelectorAll('img')];
    await Promise.all(
      images.map(async image => {
        if (image.complete && image.naturalWidth > 0) return;
        try {
          await image.decode();
        } catch (error) {
          console.error('A tiled graph image asset failed to decode.', {
            source: image.currentSrc || image.src,
            error,
          });
          throw error;
        }
      }),
    );

    const {getFontEmbedCSS, toCanvas} = await import('html-to-image');
    const fontEmbedCSS = await getFontEmbedCSS(staging);

    for (let logicalY = 0; logicalY < logicalHeight; logicalY += plan.logicalTileHeight) {
      const tileLogicalHeight = Math.min(plan.logicalTileHeight, logicalHeight - logicalY);
      const tileOutputHeight = tileLogicalHeight * plan.scale;
      const renderedTiles: RenderedTile[] = [];

      for (let logicalX = 0; logicalX < logicalWidth; logicalX += plan.logicalTileWidth) {
        const tileLogicalWidth = Math.min(plan.logicalTileWidth, logicalWidth - logicalX);
        const tileOutputWidth = tileLogicalWidth * plan.scale;
        Object.assign(staging.style, {
          width: `${tileLogicalWidth}px`,
          height: `${tileLogicalHeight}px`,
        });
        Object.assign(clone.style, {
          left: `${sourceLeft - logicalX}px`,
          top: `${sourceTop - logicalY}px`,
        });

        let canvas: HTMLCanvasElement;
        try {
          canvas = await toCanvas(staging, {
            width: tileLogicalWidth,
            height: tileLogicalHeight,
            canvasWidth: tileLogicalWidth,
            canvasHeight: tileLogicalHeight,
            pixelRatio: plan.scale,
            backgroundColor,
            cacheBust: false,
            fontEmbedCSS,
            skipAutoScale: true,
          });
        } catch (error) {
          console.error('A tiled graph canvas failed to render.', {
            logicalX,
            logicalY,
            tileLogicalWidth,
            tileLogicalHeight,
            scale: plan.scale,
            error,
          });
          throw error;
        }
        if (canvas.width !== tileOutputWidth || canvas.height !== tileOutputHeight) {
          throw new Error(
            `Tiled graph canvas produced ${canvas.width}×${canvas.height}; ` +
              `expected ${tileOutputWidth}×${tileOutputHeight}.`,
          );
        }
        const context = canvas.getContext('2d', {willReadFrequently: true});
        if (!context) throw new Error('Tiled graph canvas could not create a 2D readback context.');
        renderedTiles.push({
          outputX: logicalX * plan.scale,
          width: tileOutputWidth,
          height: tileOutputHeight,
          rgba: context.getImageData(0, 0, tileOutputWidth, tileOutputHeight).data,
        });
        canvas.width = 1;
        canvas.height = 1;
        completedTiles += 1;
        onProgress?.(completedTiles, plan.totalTiles);
        await yieldToBrowser();
      }

      const row = new Uint8Array(plan.outputWidth * 4);
      for (let tileRow = 0; tileRow < tileOutputHeight; tileRow += 1) {
        for (const tile of renderedTiles) {
          if (tile.height !== tileOutputHeight) {
            throw new Error('Tiled graph strip contains inconsistent tile heights.');
          }
          const sourceOffset = tileRow * tile.width * 4;
          row.set(
            tile.rgba.subarray(sourceOffset, sourceOffset + tile.width * 4),
            tile.outputX * 4,
          );
        }
        encoder.pushRow(row);
      }
    }

    return {blob: encoder.finish(), plan};
  } finally {
    staging.remove();
  }
}
