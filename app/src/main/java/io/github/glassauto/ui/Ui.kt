package io.github.glassauto.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** 화면 공통 색상·위젯 (XML 레이아웃 없이 코드로 구성). */
object Palette {
    val BG = Color.parseColor("#0A0E13")
    val PANEL = Color.parseColor("#121A23")
    val PANEL2 = Color.parseColor("#18222D")
    val LINE = Color.parseColor("#243241")
    val TEXT = Color.parseColor("#E8EEF4")
    val MUTED = Color.parseColor("#8C9AA8")
    val ACCENT = Color.parseColor("#38D0F0")
    val AMBER = Color.parseColor("#FFB020")
    val RED = Color.parseColor("#FF4D4F")
    val GREEN = Color.parseColor("#34D399")
    val INK = Color.parseColor("#001018")

    /** 투과율(%) → 유리 색. 100 = 투명(밝은 하늘색), 낮을수록 진함. */
    fun tint(percent: Int): Int {
        val k = Math.pow(((100 - percent) / 90.0).coerceIn(0.0, 1.0), 0.8)
        fun mix(a: Int, b: Int) = (a + (b - a) * k).toInt()
        return Color.rgb(mix(150, 12), mix(220, 18), mix(250, 26))
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 2): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke != null) setStroke(strokeWidth, stroke)
    }

fun Context.label(text: String, size: Float = 15f, color: Int = Palette.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

/** 둥근 버튼(선택 상태 표시 가능). */
fun Context.pill(text: String, selected: Boolean = false, onClick: () -> Unit): TextView =
    label(text, 16f, if (selected) Palette.INK else Palette.TEXT, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(if (selected) Palette.ACCENT else Palette.PANEL2, dp(12).toFloat(), Palette.LINE)
        setOnClickListener { onClick() }
    }

fun Context.card(vararg children: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(12), dp(16), dp(12))
    background = rounded(Palette.PANEL, dp(16).toFloat(), Palette.LINE)
    children.forEach { addView(it) }
}

fun Context.row(vararg children: View, weights: Boolean = true): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    children.forEach {
        addView(it, LinearLayout.LayoutParams(if (weights) 0 else -2, -2, if (weights) 1f else 0f).apply {
            marginEnd = dp(8)
        })
    }
}

fun gap(ctx: Context, h: Int = 12): View = View(ctx).apply { minimumHeight = ctx.dp(h) }
