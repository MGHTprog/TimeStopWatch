package com.mght.timestopwatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

/** Draws the planned agenda on one continuous axis; empty time is left visible. */
final class AgendaTimeMap extends View {
    private static final int TRACK = 0xFF343D4D;
    private static final int PAST = 0xFF5BB5A1;
    private static final int UPCOMING = 0xFF687CA4;
    private static final int LIVE = 0xFFF4C542;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<AgendaItem> items;
    private final float density;
    private int start;
    private int end;
    private int now;
    private int activeIndex = -1;

    AgendaTimeMap(Context context, List<AgendaItem> items) {
        super(context);
        this.items = items;
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void update(int start, int end, int now, int activeIndex) {
        this.start = start;
        this.end = end;
        this.now = now;
        this.activeIndex = activeIndex;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = 2 * density;
        float width = getWidth() - 2 * left;
        if (width <= 0 || end <= start) return;
        float top = 4 * density;
        float bottom = getHeight() - top;
        paint.setColor(TRACK);
        canvas.drawRoundRect(left, top, left + width, bottom, 3 * density, 3 * density, paint);
        float marker = left + width * fraction(now, start, end);
        for (int i = 0; i < items.size(); i++) {
            if (i != activeIndex) drawBlock(canvas, items.get(i), false, left, width, top, bottom, marker);
        }
        // Imported agendas can have overlapping slots. Keep the selected slot on top.
        if (activeIndex >= 0 && activeIndex < items.size()) {
            drawBlock(canvas, items.get(activeIndex), true, left, width, top, bottom, marker);
        }
        paint.setColor(Color.WHITE);
        canvas.drawRect(marker - density, density, marker + density, getHeight() - density, paint);
    }

    private void drawBlock(Canvas canvas, AgendaItem item, boolean selected,
                           float left, float width, float top, float bottom, float marker) {
        float x1 = left + width * fraction(item.startMinute * 60, start, end);
        float x2 = left + width * fraction(item.endMinute * 60, start, end);
        if (x2 <= x1) return;
        // Inset small separators without swallowing short agenda blocks.
        float inset = Math.min(density / 2, (x2 - x1) / 5);
        x1 += inset;
        x2 -= inset;
        paint.setColor(selected ? LIVE : UPCOMING);
        canvas.drawRect(x1, top, x2, bottom, paint);
        if (!selected && marker > x1) {
            paint.setColor(PAST);
            canvas.drawRect(x1, top, Math.min(x2, marker), bottom, paint);
        }
    }

    private static float fraction(int value, int start, int end) {
        return Math.max(0f, Math.min(1f, (value - start) / (float) (end - start)));
    }
}
