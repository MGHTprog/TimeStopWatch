package com.mght.timestopwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts OCR rows of an agenda table into selectable timer entries. */
final class AgendaParser {
    private static final Pattern DURATION = Pattern.compile("(?<!\\d)(\\d{1,3})\\s*[’'′分钟](?!\\d)");
    private static final Pattern CLOCK = Pattern.compile("\\b\\d{1,2}[:：]\\d{2}\\b");

    static AgendaItem parseRow(List<String> cells) {
        int durationIndex = -1;
        int minutes = 0;
        for (int i = 0; i < cells.size(); i++) {
            Matcher match = DURATION.matcher(normalize(cells.get(i)));
            if (match.find()) {
                minutes = Integer.parseInt(match.group(1));
                durationIndex = i;
                break;
            }
        }
        if (minutes <= 0 || minutes > 180) return null;

        ArrayList<String> meaningful = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            String cell = normalize(cells.get(i));
            if (cell.isEmpty() || i == durationIndex || CLOCK.matcher(cell).matches()
                    || cell.equals("开始时间") || cell.equals("结束时间") || cell.equals("时长")
                    || cell.equals("活动") || cell.equals("会议角色")) continue;
            meaningful.add(cell);
        }
        if (meaningful.isEmpty()) return null;
        String activity = meaningful.get(0);
        String role = meaningful.size() > 1 ? meaningful.get(meaningful.size() - 1) : "";
        if (activity.equals(role)) role = "";
        AgendaItem item = new AgendaItem(activity, role, minutes);
        for (String cell : cells) {
            Matcher clock = CLOCK.matcher(normalize(cell));
            while (clock.find()) {
                String[] parts = clock.group().replace('：', ':').split(":");
                int value = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                if (item.startMinute < 0) item.startMinute = value;
                else if (item.endMinute < 0) item.endMinute = value;
            }
        }
        return item;
    }

    static void completeTimes(List<AgendaItem> items) {
        int cursor = -1;
        for (AgendaItem item : items) {
            if (item.startMinute < 0) item.startMinute = cursor;
            if (item.startMinute >= 0) {
                // The printed end time wins when present; otherwise duration fills the merged table cell.
                if (item.endMinute < item.startMinute) item.endMinute = item.startMinute + item.minutes;
                cursor = item.endMinute;
            }
        }
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().replace('‘', '’').replace('`', '’');
    }

    private AgendaParser() { }
}
