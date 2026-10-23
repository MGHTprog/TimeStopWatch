package com.mght.timestopwatch;

import org.junit.Test;
import static org.junit.Assert.*;

public class TimerSessionTest {
    @Test public void pauseAndResumeExcludePausedTime() {
        TimerSession timer = new TimerSession();
        timer.start(1000);
        timer.start(1500);
        timer.pause(2500);
        assertEquals(1500, timer.elapsed(9000));
        timer.start(10000);
        assertEquals(2000, timer.elapsed(10500));
        timer.reset();
        assertFalse(timer.isRunning());
        assertEquals(0, timer.elapsed(20000));
    }

    @Test public void thresholdsChangeAtExactBoundaryAndStayRedAfterLimit() {
        assertEquals(0, TimerSession.stage(59999, 60, 90, 120));
        assertEquals(1, TimerSession.stage(60000, 60, 90, 120));
        assertEquals(2, TimerSession.stage(90000, 60, 90, 120));
        assertEquals(3, TimerSession.stage(120000, 60, 90, 120));
        assertEquals(3, TimerSession.stage(240000, 60, 90, 120));
    }

    @Test public void bellStartsExactlyThirtySecondsAfterRed() {
        assertFalse(TimerSession.isBellDue(149999, 120));
        assertTrue(TimerSession.isBellDue(150000, 120));
        assertTrue(TimerSession.isBellDue(180000, 120));
        assertFalse(TimerSession.isBellDue(449999, 420));
        assertTrue(TimerSession.isBellDue(450000, 420));
    }

    @Test public void pausedTimeDoesNotAdvanceBell() {
        TimerSession timer = new TimerSession();
        timer.start(0);
        timer.pause(149000);
        assertFalse(TimerSession.isBellDue(timer.elapsed(200000), 120));
        timer.start(200000);
        assertTrue(TimerSession.isBellDue(timer.elapsed(201000), 120));
    }

    @Test public void restoredRunningTimerIncludesTimeAwayFromActivity() {
        TimerSession timer = new TimerSession();
        timer.restore(2000, 10000, true);
        assertEquals(7000, timer.elapsed(15000));
        timer.pause(15000);
        assertEquals(7000, timer.elapsed(20000));
    }
}
