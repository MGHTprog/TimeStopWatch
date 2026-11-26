package com.mght.timestopwatch;

/** Stopwatch state; callers supply a monotonic clock in milliseconds. */
public final class TimerSession {
    private long accumulated;
    private long startedAt;
    private boolean running;

    public void start(long now) {
        if (!running) {
            startedAt = now;
            running = true;
        }
    }

    public void pause(long now) {
        if (running) {
            accumulated = elapsed(now);
            running = false;
        }
    }

    public long elapsed(long now) {
        return accumulated + (running ? Math.max(0, now - startedAt) : 0);
    }

    public boolean isRunning() { return running; }
    public long getStartedAt() { return startedAt; }
    public long getAccumulated() { return accumulated; }

    public void restore(long accumulated, long startedAt, boolean running) {
        this.accumulated = Math.max(0, accumulated);
        this.startedAt = startedAt;
        this.running = running;
    }

    public void reset() { restore(0, 0, false); }

    public static boolean isBellDue(long elapsed, int red) {
        return elapsed >= ((long) red + 30) * 1000;
    }

    static String recordStatus(long elapsed, boolean cardsEnabled, int green, int yellow, int red) {
        boolean overtime = elapsed > red * 1000L;
        if (!cardsEnabled) return overtime ? "超出计划时间" : "普通计时";
        if (overtime) return "超过红牌时间";
        switch (stage(elapsed, green, yellow, red)) {
            case 3: return "已到红牌";
            case 2: return "已到黄牌";
            case 1: return "已到绿牌";
            default: return "未到绿牌";
        }
    }

    public static int stage(long elapsed, int green, int yellow, int red) {
        long seconds = elapsed / 1000;
        if (seconds >= red) return 3;
        if (seconds >= yellow) return 2;
        if (seconds >= green) return 1;
        return 0;
    }
}
