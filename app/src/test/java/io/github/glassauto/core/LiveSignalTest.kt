package io.github.glassauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSignalTest {
    // 2026-10-28 울산 C-ITS /signal?linkId=1920174600 실제 응답 형식
    private val ulsan = """
        {"header":{"resultCode":200,"resultMsg":"신호정보를 조회하는데 성공하였습니다.","signalTotalCnt":1},
         "body":{"items":[{"intersec_no":"4","intersec_nm":"우정아이파크","signal_id":"C3700S1017",
           "realtime":[{"dir":1,"type":2,"color":1,"remain_time":151},{"dir":1,"type":1,"color":3,"remain_time":16}]}]}}
    """.trimIndent()

    // 서울 T Data 신호 잔여시간 실제 응답 형식 (잔여시간 1/10초). 두 메시지 중 최신 것을 써야 한다.
    private val tdata = """
        [{"trsmUtcTime":1793113200631,"itstId":"1537","ntStsgStatNm":"stop-And-Remain","ntStsgRmdrCs":400},
         {"trsmUtcTime":1793113260000,"itstId":"1537","nePdsgStatNm":"protected-Movement-Allowed",
          "neStsgStatNm":"protected-Movement-Allowed","neStsgRmdrCs":811.0}]
    """.trimIndent()

    @Test fun `울산 응답에서 직진 신호를 읽는다`() {
        val s = UlsanSignalParser.parse(ulsan, 1000L)!!
        assertEquals(SignalColor.GREEN, s.color)
        assertEquals(16.0, s.remainingSec, 0.0)
        assertEquals("우정아이파크", s.intersection)
    }

    @Test fun `울산 오류 응답은 무시한다`() {
        assertNull(UlsanSignalParser.parse("""{"header":{"resultCode":301,"resultMsg":"파라메터가 올바르지 않습니다."}}""", 0L))
    }

    @Test fun `T Data 는 최신 메시지의 첫 유효 방향을 1-10초 단위로 읽는다`() {
        val s = TDataSignalParser.parse(tdata)!!
        assertEquals(SignalColor.GREEN, s.color)
        assertEquals(81.1, s.remainingSec, 1e-9)
        assertEquals(1793113260000L, s.observedAtMs)
    }

    @Test fun `J2735 신호 상태를 색으로 바꾼다`() {
        assertEquals(SignalColor.RED, TDataSignalParser.colorOf("stop-And-Remain"))
        assertEquals(SignalColor.YELLOW, TDataSignalParser.colorOf("protected-clearance"))
        assertEquals(SignalColor.GREEN, TDataSignalParser.colorOf("permissive-Movement-Allowed"))
        assertNull(TDataSignalParser.colorOf("dark"))
    }

    @Test fun `받은 뒤 지난 시간만큼 줄이고 다 되면 새로 받게 한다`() {
        val live = LiveSignal(SignalColor.RED, 20.0, "우정아이파크", "test", observedAtMs = 0L)
        val base = SignalState(SignalColor.GREEN, 0.0, 30.0, 3.0, 40.0, 0.0)
        assertEquals(15.0, live.toState(5_000L, 300.0, base)!!.remainingSec, 1e-9)
        assertNull(live.toState(21_000L, 300.0, base))
    }
}
