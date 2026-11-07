package com.mght.timestopwatch;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AgendaRecognizer {
    interface Callback {
        void success(List<AgendaItem> items);
        void failure(Exception error);
    }

    private static final class Cell {
        final String text;
        final Rect box;
        Cell(String text, Rect box) { this.text = text; this.box = box; }
    }

    static void recognize(Context context, Uri uri, Callback callback) {
        final InputImage image;
        try { image = InputImage.fromFilePath(context, uri); }
        catch (IOException error) { callback.failure(error); return; }
        TextRecognizer recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    callback.success(toItems(text));
                    recognizer.close();
                })
                .addOnFailureListener(error -> {
                    callback.failure(error);
                    recognizer.close();
                });
    }

    private static List<AgendaItem> toItems(Text result) {
        ArrayList<Cell> cells = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null) cells.add(new Cell(line.getText(), box));
            }
        }
        cells.sort(Comparator.comparingInt(cell -> cell.box.centerY()));
        ArrayList<List<Cell>> rows = new ArrayList<>();
        for (Cell cell : cells) {
            List<Cell> row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (row == null || !sameRow(row, cell)) {
                row = new ArrayList<>();
                rows.add(row);
            }
            row.add(cell);
        }
        ArrayList<AgendaItem> items = new ArrayList<>();
        for (List<Cell> row : rows) {
            row.sort(Comparator.comparingInt(cell -> cell.box.left));
            ArrayList<String> texts = new ArrayList<>();
            for (Cell cell : row) texts.add(cell.text);
            AgendaItem item = AgendaParser.parseRow(texts);
            if (item != null) items.add(item);
        }
        AgendaParser.completeTimes(items);
        return items;
    }

    private static boolean sameRow(List<Cell> row, Cell candidate) {
        Cell previous = row.get(0);
        int tolerance = Math.max(12, Math.min(previous.box.height(), candidate.box.height()) * 2 / 3);
        return Math.abs(previous.box.centerY() - candidate.box.centerY()) <= tolerance;
    }

    private AgendaRecognizer() { }
}
