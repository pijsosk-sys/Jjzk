package com.boddy.os.vnc;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * ZRLE (RFC 6143 section 7.7.6) for RfbClient: rectangles in 64x64 tiles, each one colour, a few colours,
 * runs, or plain pixels, all through one zlib stream per connection. A desktop is mostly flat colours and
 * text, so it needs a fraction of Raw's 4 bytes per pixel. One instance per connection (the zlib stream
 * continues from rectangle to rectangle); used only on RfbClient's reading thread.
 *
 * Pixels are RfbClient's format (32 bits, depth 24, little-endian, red at bit 16), which ZRLE sends as
 * 3-byte "CPIXELs": blue, green, red.
 */
public final class Zrle {
    private static final int TILE = 64;
    private final Inflater inflater = new Inflater();
    private byte[] packed = new byte[64 * 1024];
    private byte[] data = new byte[256 * 1024];
    private int pos, end;
    private final int[] tile = new int[TILE * TILE];
    private final int[] palette = new int[128];

    /** Reads one ZRLE rectangle at x,y (w x h) from `in` into `client`'s framebuffer. */
    public void read(RfbClient client, DataInputStream in, int x, int y, int w, int h) throws IOException {
        int length = in.readInt();
        if (length < 0) throw new IOException("Bad ZRLE length " + length);
        if (packed.length < length) packed = new byte[length];
        in.readFully(packed, 0, length);
        inflate(length);
        for (int ty = y; ty < y + h; ty += TILE) {
            int th = Math.min(TILE, y + h - ty);
            for (int tx = x; tx < x + w; tx += TILE) {
                int tw = Math.min(TILE, x + w - tx);
                readTile(tw, th);
                store(client, tx, ty, tw, th);
            }
        }
        if (pos != end) throw new IOException("ZRLE rectangle has " + (end - pos) + " bytes left over");
    }

    /** Unpacks everything the server sent for this rectangle (it flushes its zlib stream after each one). */
    private void inflate(int length) throws IOException {
        inflater.setInput(packed, 0, length);
        pos = 0;
        end = 0;
        try {
            while (true) {
                if (end == data.length) {
                    byte[] bigger = new byte[data.length * 2];
                    System.arraycopy(data, 0, bigger, 0, end);
                    data = bigger;
                }
                int n = inflater.inflate(data, end, data.length - end);
                end += n;
                if (n == 0 && (inflater.needsInput() || inflater.finished())) break;
                if (n == 0 && inflater.needsDictionary()) throw new IOException("ZRLE stream needs a dictionary");
            }
        } catch (DataFormatException e) {
            throw new IOException("Bad ZRLE data: " + e.getMessage());
        }
    }

    private int u8() throws IOException {
        if (pos >= end) throw new IOException("ZRLE data ended early");
        return data[pos++] & 0xff;
    }

    private int cpixel() throws IOException {
        if (pos + 3 > end) throw new IOException("ZRLE data ended early");
        int p = 0xff000000 | (data[pos] & 0xff) | ((data[pos + 1] & 0xff) << 8) | ((data[pos + 2] & 0xff) << 16);
        pos += 3;
        return p;
    }

    /** A run length: 1 + the sum of its bytes, which go on while they are 255. */
    private int runLength() throws IOException {
        int len = 1, b;
        do {
            b = u8();
            len += b;
        } while (b == 255);
        return len;
    }

    private void readTile(int tw, int th) throws IOException {
        int n = tw * th;
        int mode = u8();
        if (mode == 0) {                                   // raw
            for (int i = 0; i < n; i++) tile[i] = cpixel();
        } else if (mode == 1) {                            // one colour
            int c = cpixel();
            for (int i = 0; i < n; i++) tile[i] = c;
        } else if (mode <= 16) {                           // a palette of 2-16 colours, indexes packed in bytes
            for (int i = 0; i < mode; i++) palette[i] = cpixel();
            int bits = mode == 2 ? 1 : mode <= 4 ? 2 : 4;
            int mask = (1 << bits) - 1;
            for (int row = 0; row < th; row++) {          // each row starts on a new byte
                int b = 0, left = 0;
                for (int col = 0; col < tw; col++) {
                    if (left == 0) {
                        b = u8();
                        left = 8;
                    }
                    left -= bits;
                    int index = (b >> left) & mask;
                    if (index >= mode) throw new IOException("ZRLE palette index out of range");
                    tile[row * tw + col] = palette[index];
                }
            }
        } else if (mode == 128) {                          // runs of one colour
            for (int i = 0; i < n; ) {
                int c = cpixel();
                int len = runLength();
                if (i + len > n) throw new IOException("ZRLE run too long");
                for (int k = 0; k < len; k++) tile[i++] = c;
            }
        } else if (mode >= 130) {                          // runs of palette colours
            int size = mode - 128;
            for (int i = 0; i < size; i++) palette[i] = cpixel();
            for (int i = 0; i < n; ) {
                int index = u8();
                int len = 1;
                if ((index & 128) != 0) {
                    index &= 127;
                    len = runLength();
                }
                if (index >= size) throw new IOException("ZRLE palette index out of range");
                if (i + len > n) throw new IOException("ZRLE run too long");
                for (int k = 0; k < len; k++) tile[i++] = palette[index];
            }
        } else {
            throw new IOException("Unknown ZRLE tile type " + mode);
        }
    }

    /** Copies the decoded tile into the framebuffer, under its lock, as RfbClient's Raw does. */
    private void store(RfbClient client, int tx, int ty, int tw, int th) {
        synchronized (client.getLock()) {
            int[] pixels = client.getPixels();
            int width = client.getWidth();
            int height = client.getHeight();
            if (tx + tw > width || ty + th > height) return;  // off a screen that has just been resized
            for (int row = 0; row < th; row++) {
                System.arraycopy(tile, row * tw, pixels, (ty + row) * width + tx, tw);
            }
        }
    }
}
