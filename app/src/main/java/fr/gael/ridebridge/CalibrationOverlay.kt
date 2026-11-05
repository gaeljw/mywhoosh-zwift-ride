package fr.gael.ridebridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * Full-screen, see-through overlay drawn by the accessibility service on top of MyWhoosh
 * (no "draw over other apps" permission needed). Drag the two markers onto the gear buttons, then Save.
 */
@SuppressLint("ClickableViewAccessibility")
class CalibrationOverlay(service: TapService, private val onClosed: () -> Unit) {

    private val ctx: Context = ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = Prefs(service)
    private val root = FrameLayout(ctx)
    private val markers = mutableMapOf<Direction, Marker>()
    private var shown = false

    fun show() {
        val dp = ctx.resources.displayMetrics.density
        val size = (72 * dp).toInt()
        val (w, h) = Screen.size(ctx)
        root.setBackgroundColor(0x40000000)

        for (dir in Direction.entries) {
            val marker = Marker(ctx, if (dir == Direction.UP) "+" else "−")
            val (cx, cy) = prefs.point(dir)?.let { Screen.adjust(ctx, it) }
                ?: ((w * 0.85f) to (h * if (dir == Direction.UP) 0.4f else 0.6f))
            marker.x = cx - size / 2f
            marker.y = cy - size / 2f
            makeDraggable(marker)
            root.addView(marker, FrameLayout.LayoutParams(size, size))
            markers[dir] = marker
        }

        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            val pad = (12 * dp).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(TextView(ctx).apply {
                text = "Drag + onto gear up and − onto gear down"
                setTextColor(Color.WHITE)
                setPadding(0, 0, pad, 0)
            })
            addView(Button(ctx).apply { text = "Save"; setOnClickListener { save() } })
            addView(Button(ctx).apply { text = "Cancel"; setOnClickListener { dismiss() } })
        }
        root.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = (16 * dp).toInt() })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(root, params)
        shown = true
    }

    fun dismiss() {
        if (shown) runCatching { wm.removeView(root) }
        shown = false
        onClosed()
    }

    private fun save() {
        val rotation = Screen.rotation(ctx)
        for ((dir, marker) in markers) {
            val loc = IntArray(2)
            marker.getLocationOnScreen(loc)
            prefs.setPoint(dir, TapPoint(loc[0] + marker.width / 2f, loc[1] + marker.height / 2f, rotation))
        }
        Hub.update(lastEvent = "Tap points saved")
        dismiss()
    }

    private fun makeDraggable(view: View) {
        var dx = 0f
        var dy = 0f
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = v.x - e.rawX; dy = v.y - e.rawY }
                MotionEvent.ACTION_MOVE -> { v.x = e.rawX + dx; v.y = e.rawY + dy }
            }
            true
        }
    }

    /** A ring with a centre dot: the dot is the exact tap position. */
    private class Marker(context: Context, private val label: String) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFFFF5722.toInt()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FF5722 }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = min(cx, cy) - 4f
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, ring)
            canvas.drawCircle(cx, cy, 5f, dot)
            text.textSize = r * 0.6f
            canvas.drawText(label, cx, cy - r * 0.35f, text)
        }
    }
}
