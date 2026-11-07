package com.mght.timestopwatch;

final class AgendaItem {
    String activity;
    String role;
    int minutes;
    String section = "";
    boolean inferred;
    boolean requiresCards = true;
    int startMinute = -1;
    int endMinute = -1;

    AgendaItem(String activity, String role, int minutes) {
        this.activity = activity;
        this.role = role;
        this.minutes = minutes;
    }

    String label() {
        String time = startMinute < 0 ? "" : clock(startMinute) + "–" + clock(endMinute) + " · ";
        return time + minutes + "分钟 · " + activity + (role.isEmpty() ? "" : " · " + role)
                + (requiresCards ? "" : " · 不举牌") + (inferred ? " · AI推算" : "");
    }

    static String clock(int minute) {
        minute = (minute + 1440) % 1440;
        return String.format(java.util.Locale.CHINA, "%02d:%02d", minute / 60, minute % 60);
    }
}
