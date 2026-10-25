package io.github.glassauto.emergency

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import io.github.glassauto.core.EmergencyStage
import io.github.glassauto.vehicle.VehicleGateway
import java.util.Locale

/** 긴급차량 알림 방식 (긴급차량 탭에서 각각 켜고 끔). */
data class AlertSettings(
    var voice: Boolean = true,
    var duckMedia: Boolean = true,
    var ambientWarning: Boolean = true,
    var yieldGuide: Boolean = true,
)

/**
 * 긴급차량 안내 기능의 차량 반응. 유리(썬팅) 기능과 독립적이며 유리는 건드리지 않는다.
 * 1km 음성 예고 → 300m 볼륨 낮춤·조명 경고·양보 안내 → 통과 후 복귀.
 */
class AlertController(
    context: Context,
    private val gateway: () -> VehicleGateway,
    val settings: AlertSettings = AlertSettings(),
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("glassauto", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var focus: AudioFocusRequest? = null
    private var last = EmergencyStage.NONE

    val yieldCount: Int get() = prefs.getInt(KEY_YIELD, 0)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.KOREAN
        }
    }

    /** 단계가 바뀔 때만 반응한다. 반환값: 화면에 보여 줄 안내 문구. */
    fun onStage(stage: EmergencyStage): String? {
        if (stage == last) return null
        last = stage
        return when (stage) {
            EmergencyStage.PRE_ALERT -> say("뒤쪽 1킬로미터에서 구급차가 같은 방향으로 접근 중입니다.")
            EmergencyStage.ALERT -> {
                if (settings.duckMedia) duck(true)
                if (settings.ambientWarning) runCatching { gateway().setAmbientWarning(true) }
                val msg = if (settings.yieldGuide) "구급차가 300미터 뒤에 있습니다. 오른쪽 차로로 비켜 주세요." else "구급차가 300미터 뒤에 있습니다."
                say(msg)
            }
            EmergencyStage.PASSED -> {
                restore()
                prefs.edit().putInt(KEY_YIELD, yieldCount + 1).apply()
                say("구급차가 지나갔습니다. 양보해 주셔서 감사합니다.")
            }
            EmergencyStage.NONE -> { restore(); null }
        }
    }

    private fun say(text: String): String {
        if (settings.voice && ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "glassauto")
        return text
    }

    /** 다른 앱의 음악을 일시적으로 낮춘다(오디오 포커스 TRANSIENT_MAY_DUCK). */
    private fun duck(on: Boolean) {
        if (on && focus == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build().also { audio.requestAudioFocus(it) }
        } else if (!on) {
            focus?.let { audio.abandonAudioFocusRequest(it) }
            focus = null
        }
    }

    private fun restore() {
        duck(false)
        runCatching { gateway().setAmbientWarning(false) }
    }

    fun release() {
        restore()
        tts?.shutdown()
        tts = null
    }

    companion object { private const val KEY_YIELD = "yield_count" }
}
