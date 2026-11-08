package fr.gael.ridebridge

import java.util.UUID

/**
 * Zwift Ride BLE protocol, as reverse-engineered by BikeControl
 * (github.com/jonasbark/swiftcontrol, lib/bluetooth/devices/zwift/).
 *
 * The Ride advertises two halves: left (manufacturer byte 0x08) and right (0x07). Either one is
 * connected to. On a Ride v1 (firmware < 1.3) the left hood reports the buttons of both hoods.
 */
object Zwift {
    const val MANUFACTURER_ID = 0x094A
    const val RIDE_LEFT = 0x08
    const val RIDE_RIGHT = 0x07

    val SERVICE_RIDE: UUID = UUID.fromString("0000fc82-0000-1000-8000-00805f9b34fb")
    val SERVICE_LEGACY: UUID = UUID.fromString("00000001-19ca-4651-86e5-fa29dcdd09d1")
    val CHAR_ASYNC: UUID = UUID.fromString("00000002-19ca-4651-86e5-fa29dcdd09d1")   // notifications
    val CHAR_SYNC_RX: UUID = UUID.fromString("00000003-19ca-4651-86e5-fa29dcdd09d1") // we write here
    val CHAR_SYNC_TX: UUID = UUID.fromString("00000004-19ca-4651-86e5-fa29dcdd09d1") // indications
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val DIS_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

    val RIDE_ON = "RideOn".toByteArray(Charsets.US_ASCII)
    val VIBRATE = byteArrayOf(0x12, 0x12, 0x08, 0x0A, 0x06, 0x08, 0x02, 0x10, 0x00, 0x18, 0x20)

    const val OP_CONTROLLER_NOTIFICATION = 0x23
}

/** Digital buttons in the RideKeyPadStatus.ButtonMap. Bits are active-low: 0 means pressed. */
enum class RideButton(val mask: Int, val label: String) {
    LEFT(0x0001, "Left hood ◀"),
    UP(0x0002, "Left hood ▲"),
    RIGHT(0x0004, "Left hood ▶"),
    DOWN(0x0008, "Left hood ▼"),
    A(0x0010, "Right hood A"),
    B(0x0020, "Right hood B"),
    Y(0x0040, "Right hood Y"),
    Z(0x0080, "Right hood Z"),
    SHIFT_UP_LEFT(0x0100, "Left shift up"),
    SHIFT_DOWN_LEFT(0x0200, "Left shift down"),
    POWER_UP_LEFT(0x0400, "Left power-up"),
    ON_OFF_LEFT(0x0800, "Left on/off"),
    SHIFT_UP_RIGHT(0x1000, "Right shift up"),
    SHIFT_DOWN_RIGHT(0x2000, "Right shift down"),
    POWER_UP_RIGHT(0x4000, "Right power-up"),
    ON_OFF_RIGHT(0x8000, "Right on/off");

    companion object {
        const val ALL_MASK = 0xFFFF
        fun labels(mask: Int): String =
            entries.filter { (it.mask and mask) != 0 }.joinToString { it.label }.ifEmpty { "0x%04x".format(mask) }
    }
}

/** Just enough protobuf decoding to read field 1 (ButtonMap) of a RideKeyPadStatus message. */
object RideKeyPad {
    fun buttonMap(frame: ByteArray, offset: Int): Int? {
        var i = offset
        while (i < frame.size) {
            val tag = readVarint(frame, i) ?: return null
            i = tag.second
            val field = (tag.first ushr 3).toInt()
            when ((tag.first and 7L).toInt()) {
                0 -> {
                    val v = readVarint(frame, i) ?: return null
                    i = v.second
                    if (field == 1) return v.first.toInt()
                }
                1 -> i += 8
                2 -> {
                    val len = readVarint(frame, i) ?: return null
                    // Untrusted input: a negative or oversized length would move i backwards (endless loop) or past the end.
                    if (len.first < 0 || len.first > frame.size - len.second) return null
                    i = len.second + len.first.toInt()
                }
                5 -> i += 4
                else -> return null
            }
        }
        return null
    }

    private fun readVarint(b: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var i = start
        while (i < b.size && shift < 64) {
            val byte = b[i].toInt() and 0xFF
            i++
            result = result or ((byte and 0x7F).toLong() shl shift)
            if ((byte and 0x80) == 0) return result to i
            shift += 7
        }
        return null
    }
}
