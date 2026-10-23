package com.mght.timestopwatch;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Canonical 16kHz mono PCM WAV. Headers are refreshed so interrupted audio is recoverable. */
public final class WavFile implements AutoCloseable {
    private final RandomAccessFile output;
    private long dataBytes;
    private long lastHeader;

    public WavFile(File file) throws IOException {
        output = new RandomAccessFile(file, "rw");
        output.setLength(0);
        header();
    }

    public void write(short[] pcm, int count) throws IOException {
        byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            bytes[i * 2] = (byte) pcm[i];
            bytes[i * 2 + 1] = (byte) (pcm[i] >> 8);
        }
        output.write(bytes);
        dataBytes += bytes.length;
        if (dataBytes - lastHeader >= 32000) header();
    }

    private void header() throws IOException {
        output.seek(0);
        output.writeBytes("RIFF"); little((int) (dataBytes + 36), 4);
        output.writeBytes("WAVEfmt "); little(16, 4); little(1, 2); little(1, 2);
        little(16000, 4); little(32000, 4); little(2, 2); little(16, 2);
        output.writeBytes("data"); little((int) dataBytes, 4);
        output.seek(44 + dataBytes);
        lastHeader = dataBytes;
    }

    private void little(int value, int count) throws IOException {
        for (int i = 0; i < count; i++) output.write((value >>> (8 * i)) & 255);
    }

    @Override public void close() throws IOException {
        try { header(); } finally { output.close(); }
    }
}
