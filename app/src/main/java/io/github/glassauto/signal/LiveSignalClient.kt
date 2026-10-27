package io.github.glassauto.signal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.glassauto.core.LiveSignal
import io.github.glassauto.core.TDataSignalParser
import io.github.glassauto.core.UlsanSignalParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * 공공 실시간 신호 API 클라이언트 (시연용).
 * 인증키는 APK에 넣지 않는다. run.ps1 이 .env 의 키를 앱 실행 인자로 한 번 넘기면
 * [saveKeys] 가 앱 전용 저장소(SharedPreferences)에 보관하고, 그때부터 동작한다.
 * 실서비스에서는 서버가 키를 보관하고 호출량을 관리한 뒤 차량에 신호만 나눠 준다.
 */
class LiveSignalClient(context: Context) {
    private val keys = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var lastCallMs = 0L

    val ulsanKey: String? get() = keys.getString(ULSAN_KEY, null)?.takeIf { it.isNotBlank() }
    val tdataKey: String? get() = keys.getString(TDATA_KEY, null)?.takeIf { it.isNotBlank() }
    val available: Boolean get() = ulsanKey != null || tdataKey != null

    /** 울산 C-ITS 우선, 없으면 서울 T Data. 너무 잦은 호출은 막는다. */
    fun fetch(done: (Result<LiveSignal>) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastCallMs < MIN_INTERVAL_MS) { done(Result.failure(IllegalStateException("잠시 후 다시 받아옵니다"))); return }
        lastCallMs = now
        thread(name = "live-signal") {
            val r = runCatching {
                val uk = ulsanKey
                if (uk != null) {
                    val url = "$ULSAN_URL?serviceKey=${enc(uk)}&linkId=$ULSAN_LINK"
                    UlsanSignalParser.parse(get(url), System.currentTimeMillis()) ?: error("울산 신호 응답에 직진 신호가 없습니다")
                } else {
                    val url = "$TDATA_URL?apiKey=${enc(tdataKey!!)}&type=json&pageNo=1&numOfRows=10&itstId=$TDATA_ITST"
                    TDataSignalParser.parse(get(url)) ?: error("T Data 응답에 직진 신호가 없습니다")
                }
            }
            r.exceptionOrNull()?.let { Log.w(TAG, "live signal", it) }
            main.post { done(r) }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val TAG = "GlassAuto/Signal"
        private const val PREFS = "signal_keys"
        const val ULSAN_KEY = "DATA_GO_KR_SERVICE_KEY"
        const val TDATA_KEY = "TDATA_API_KEY"

        /** run.ps1 이 실행 인자(extra)로 넘긴 키를 저장한다. 빈 값은 무시한다. */
        fun saveKeys(context: Context, extras: android.os.Bundle?) {
            extras ?: return
            val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            listOf(ULSAN_KEY, TDATA_KEY).forEach { k -> extras.getString(k)?.takeIf { it.isNotBlank() }?.let { e.putString(k, it) } }
            e.apply()
        }
        private const val MIN_INTERVAL_MS = 5_000L
        private const val ULSAN_URL = "https://apis.data.go.kr/6310000/citsapi/service/signal"
        const val ULSAN_LINK = "1920174600"          // 울산 우정아이파크 교차로 진입 링크 (API 문서 예시)
        private const val TDATA_URL = "https://t-data.seoul.go.kr/apig/apiman-gateway/tapi/v2xSignalPhaseTimingFusionInformation/1.0"
        private const val TDATA_ITST = "1537"        // T Data API 문서 예시 교차로
    }
}
