package com.mght.timestopwatch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AgendaTimeMapDeviceTest {
    @Test public void selectedBlockStaysVisibleAndClockMarkerMoves() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            AgendaTimeMap map = new AgendaTimeMap(context, Arrays.asList(
                    item(0, 15), item(10, 20), item(30, 60)));
            map.layout(0, 0, 480, 80);
            Bitmap bitmap = Bitmap.createBitmap(480, 80, Bitmap.Config.ARGB_8888);
            try {
                float left = 2 * context.getResources().getDisplayMetrics().density;
                map.update(0, 3600, 8 * 60, 0);
                map.draw(new Canvas(bitmap));
                assertEquals("Selected slot remains yellow inside the overlap", 0xFFF4C542,
                        bitmap.getPixel(x(left, 12), 40));
                assertEquals("Gap remains unfilled", 0xFF343D4D, bitmap.getPixel(x(left, 25), 40));
                assertEquals("First clock position", Color.WHITE, bitmap.getPixel(x(left, 8), 40));
                map.update(0, 3600, 36 * 60, 0);
                bitmap.eraseColor(Color.TRANSPARENT);
                map.draw(new Canvas(bitmap));
                assertEquals("Clock moved", Color.WHITE, bitmap.getPixel(x(left, 36), 40));
                assertEquals("Old clock position is no longer white", 0xFFF4C542,
                        bitmap.getPixel(x(left, 8), 40));
            } finally {
                bitmap.recycle();
            }
        });
    }

    private static int x(float left, int minute) {
        return Math.round(left + (480 - 2 * left) * minute / 60f);
    }

    private static AgendaItem item(int start, int end) {
        AgendaItem item = new AgendaItem("测试环节", "", end - start);
        item.startMinute = start;
        item.endMinute = end;
        return item;
    }
}
