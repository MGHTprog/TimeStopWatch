package com.mght.timestopwatch;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AgendaParserTest {
    @Test public void parsesTypicalAgendaRow() {
        AgendaItem item = AgendaParser.parseRow(Arrays.asList("19:21", "19:28", "《给自己一点时间》", "7’", "MG HT"));
        assertNotNull(item);
        assertEquals("《给自己一点时间》", item.activity);
        assertEquals("MG HT", item.role);
        assertEquals(7, item.minutes);
    }

    @Test public void parsesChineseMinuteSuffix() {
        AgendaItem item = AgendaParser.parseRow(Arrays.asList("即兴演讲：游", "25分钟", "糖糖"));
        assertNotNull(item);
        assertEquals(25, item.minutes);
    }

    @Test public void ignoresHeadersWithoutDuration() {
        assertNull(AgendaParser.parseRow(Arrays.asList("开始时间", "结束时间", "活动", "时长", "会议角色")));
    }
}
