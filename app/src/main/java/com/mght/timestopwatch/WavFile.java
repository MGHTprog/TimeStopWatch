package com.mght.timestopwatch;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Canonical 16kHz mono PCM WAV. Headers are refreshed so interrupted audio is recoverable. */
public final class WavFile implements AutoCloseable {
    private final RandomAccessFile output;
    private long dataBytes;
    private long lastHeader;
    private byte[] bytes = new byte[640];
    private final ByteBuffer headerBytes = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);

    public WavFile(File file) throws IOException {
        output = new RandomAccessFile(file, "rw");
        output.setLength(0);
        header();
    }

    public void write(short[] pcm, int count) throws IOException {
        if (count < 0 || count > pcm.length) throw new IndexOutOfBoundsException("Invalid PCM count");
        int length = count * 2;
        if (bytes.length < length) bytes = new byte[length];
        for (int i = 0; i < count; i++) {
            bytes[i * 2] = (byte) pcm[i];
            bytes[i * 2 + 1] = (byte) (pcm[i] >> 8);
        }
        output.write(bytes, 0, length);
        dataBytes += length;
        if (dataBytes - lastHeader >= 32000) header();
    }

    private void header() throws IOException {
        headerBytes.clear();
        headerBytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt((int) (dataBytes + 36));
        headerBytes.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        headerBytes.putShort((short) 1).putShort((short) 1);
        headerBytes.putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        headerBytes.put("data".getBytes(StandardCharsets.US_ASCII)).putInt((int) dataBytes);
        output.seek(0);
        output.write(headerBytes.array());
        output.seek(44 + dataBytes);
        lastHeader = dataBytes;
    }

    @Override public void close() throws IOException {
        try { header(); } finally { output.close(); }
    }
}
