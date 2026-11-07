package fr.gael.ridebridge

import android.content.Context

enum class Feedback(val label: String) { OFF("Off"), CONTROLLER("Vibrate the controller"), PHONE("Vibrate the phone") }

/** A tap position in screen pixels, with the rotation it was calibrated in. */
data class TapPoint(val x: Float, val y: Float, val rotation: Int)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ridebridge", Context.MODE_PRIVATE)

    fun mask(dir: Direction): Int = sp.getInt(
        "mask_$dir",
        if (dir == Direction.UP) RideButton.SHIFT_UP_LEFT.mask else RideButton.SHIFT_DOWN_LEFT.mask,
    )

    fun setMask(dir: Direction, mask: Int) = sp.edit().putInt("mask_$dir", mask).apply()

    fun point(dir: Direction): TapPoint? {
        if (!sp.contains("x_$dir")) return null
        return TapPoint(sp.getFloat("x_$dir", 0f), sp.getFloat("y_$dir", 0f), sp.getInt("rot_$dir", 1))
    }

    fun setPoint(dir: Direction, p: TapPoint) = sp.edit()
        .putFloat("x_$dir", p.x).putFloat("y_$dir", p.y).putInt("rot_$dir", p.rotation).apply()

    /** Address of the controller to talk to; set on the first successful connection. */
    var controller: String?
        get() = sp.getString("controller", null)
        set(v) = sp.edit().apply { if (v == null) remove("controller") else putString("controller", v) }.apply()

    var feedback: Feedback
        get() = runCatching { Feedback.valueOf(sp.getString("feedback", null)!!) }.getOrDefault(Feedback.CONTROLLER)
        set(v) = sp.edit().putString("feedback", v.name).apply()
}
