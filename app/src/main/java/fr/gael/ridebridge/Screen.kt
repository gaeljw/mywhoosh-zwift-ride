package fr.gael.ridebridge

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager

object Screen {
    fun rotation(ctx: Context): Int =
        ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).rotation

    fun size(ctx: Context): Pair<Int, Int> {
        val b = ctx.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return b.width() to b.height()
    }

    /**
     * Converts a saved point to the current rotation. The two landscape orientations are a
     * 180° flip of each other, so a point calibrated in one still works when the phone is turned around.
     */
    fun adjust(ctx: Context, p: TapPoint): Pair<Float, Float> {
        val rot = rotation(ctx)
        fun landscape(r: Int) = r == Surface.ROTATION_90 || r == Surface.ROTATION_270
        if (rot != p.rotation && landscape(rot) && landscape(p.rotation)) {
            val (w, h) = size(ctx)
            return (w - p.x) to (h - p.y)
        }
        return p.x to p.y
    }
}
