package com.mght.timestopwatch;

import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import static org.junit.Assert.*;

public class WavFileTest {
    @Test public void writesPlayablePcmAndFinalHeader() throws Exception {
        File file = File.createTempFile("speech-audio", ".wav");
        try {
            try (WavFile output = new WavFile(file)) {
                output.write(new short[]{0, 32767, -32768, -1}, 4);
                output.write(new short[]{1234}, 1);
            }
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                assertEquals(54, input.length());
                assertEquals(0x52494646, input.readInt());
                input.seek(22); assertEquals(1, input.readUnsignedByte());
                input.seek(24); assertEquals(16000, Integer.reverseBytes(input.readInt()));
                input.seek(40); assertEquals(10, Integer.reverseBytes(input.readInt()));
                input.seek(46); assertEquals(32767, Short.reverseBytes(input.readShort()));
                assertEquals(-32768, Short.reverseBytes(input.readShort()));
                input.seek(52); assertEquals(1234, Short.reverseBytes(input.readShort()));
            }
        } finally { file.delete(); }
    }

    @Test public void preservesEverySampleAcrossHeaderRefreshesAndShortReads() throws Exception {
        File file = File.createTempFile("speech-continuity", ".wav");
        int total = 0;
        try {
            try (WavFile output = new WavFile(file)) {
                short[] pcm = new short[8000];
                for (int block = 0; block < 250; block++) {
                    int count = block == 0 ? pcm.length : (block % 319) + 1;
                    for (int j = 0; j < count; j++) pcm[j] = (short) (total + j);
                    output.write(pcm, count);
                    total += count;
                }
            }
            try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                assertEquals(44L + total * 2L, input.length());
                input.seek(40);
                assertEquals(total * 2, Integer.reverseBytes(input.readInt()));
                for (int j = 0; j < total; j++)
                    assertEquals("PCM sample " + j, (short) j, Short.reverseBytes(input.readShort()));
            }
        } finally { file.delete(); }
    }

    @Test public void headerIsRefreshedBeforeCloseForRecoverableLongRecordings() throws Exception {
        File file = File.createTempFile("speech-recover", ".wav");
        try {
            try (WavFile output = new WavFile(file)) {
                output.write(new short[16000], 16000);
                try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                    input.seek(40); assertEquals(32000, Integer.reverseBytes(input.readInt()));
                }
                output.write(new short[]{42}, 1);
            }
            assertEquals(32046, file.length());
        } finally { file.delete(); }
    }
}
