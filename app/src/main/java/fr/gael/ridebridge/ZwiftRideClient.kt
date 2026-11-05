package fr.gael.ridebridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Finds the Zwift Ride's left hood, connects, runs the "RideOn" handshake and reports button presses.
 * Reconnects on its own. All listener calls happen on the main thread.
 * Permissions are checked by the activity before the service starts.
 */
@SuppressLint("MissingPermission")
class ZwiftRideClient(context: Context, private val listener: Listener) {

    interface Listener {
        fun onStatus(text: String)
        fun onConnected(firmware: String?)
        fun onDisconnected()
        /** Bit mask (see [RideButton]) of buttons that went from released to pressed. */
        fun onButtonsPressed(newlyPressed: Int)
    }

    private val ctx = context.applicationContext
    private val adapter: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())

    private var running = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null
    private var syncRx: BluetoothGattCharacteristic? = null
    private var ready = false
    private var firmware: String? = null
    private var pressed = 0
    private var reconnectDelayMs = 2_000L

    /** Android runs one GATT operation at a time: each step starts one and returns true to wait for its callback. */
    private val steps = ArrayDeque<(BluetoothGatt) -> Boolean>()

    fun start() {
        if (running) return
        running = true
        startScan()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        stopScan()
        gatt?.let { it.disconnect(); it.close() }
        gatt = null
        syncRx = null
        ready = false
    }

    fun vibrate() {
        if (!ready) return
        val g = gatt ?: return
        val ch = syncRx ?: return
        g.writeCharacteristic(ch, Zwift.VIBRATE, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    // ---- Scanning -------------------------------------------------------------------------

    private fun startScan() {
        if (!running || scanning || gatt != null) return
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            listener.onStatus("Bluetooth is off")
            retry(5_000)
            return
        }
        // A filtered scan keeps working with the screen off.
        val filter = ScanFilter.Builder()
            .setManufacturerData(Zwift.MANUFACTURER_ID, byteArrayOf(Zwift.RIDE_LEFT.toByte()), byteArrayOf(0xFF.toByte()))
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        scanning = true
        listener.onStatus("Searching for the Zwift Ride (left hood)… press a button to wake it")
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun retry(delayMs: Long) {
        if (running) main.postDelayed({ startScan() }, delayMs)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post {
                if (!scanning) return@post
                stopScan()
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                scanning = false
                listener.onStatus("Scan failed (code $errorCode), retrying")
                retry(5_000)
            }
        }
    }

    private fun connect(device: BluetoothDevice) {
        listener.onStatus("Connecting to ${device.name ?: device.address}…")
        gatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // ---- GATT -----------------------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (gatt !== g) return@post
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        listener.onStatus("Connected, discovering services…")
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        g.close()
                        gatt = null
                        syncRx = null
                        ready = false
                        steps.clear()
                        pressed = 0
                        listener.onDisconnected()
                        // Keep the "service not found" explanation visible during the long retry.
                        if (running && reconnectDelayMs <= 2_000) {
                            listener.onStatus("Disconnected (status $status), searching again…")
                        }
                        retry(reconnectDelayMs)
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post { if (gatt === g) setUp(g) }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int,
        ) {
            main.post {
                if (characteristic.uuid == Zwift.FIRMWARE_REVISION && status == BluetoothGatt.GATT_SUCCESS) {
                    firmware = String(value, Charsets.US_ASCII).trim()
                }
                nextStep()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post { nextStep() }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
        ) {
            main.post { handleFrame(value) }
        }
    }

    private fun setUp(g: BluetoothGatt) {
        val service = g.getService(Zwift.SERVICE_RIDE) ?: g.getService(Zwift.SERVICE_LEGACY)
        val asyncCh = service?.getCharacteristic(Zwift.CHAR_ASYNC)
        val txCh = service?.getCharacteristic(Zwift.CHAR_SYNC_TX)
        val rxCh = service?.getCharacteristic(Zwift.CHAR_SYNC_RX)
        val fwCh = g.getService(Zwift.DIS_SERVICE)?.getCharacteristic(Zwift.FIRMWARE_REVISION)

        steps.clear()
        if (fwCh != null) steps.add { it.readCharacteristic(fwCh) }

        if (asyncCh == null || txCh == null || rxCh == null) {
            steps.add {
                listener.onStatus(
                    "Zwift service not found (firmware ${firmware ?: "unknown"}). A Zwift Ride on firmware 1.3+ " +
                        "hides it until Zwift unlocks it; very old firmware needs an update in Zwift Companion. " +
                        "Retrying in 30 s."
                )
                reconnectDelayMs = 30_000
                it.disconnect()
                false
            }
        } else {
            reconnectDelayMs = 2_000
            syncRx = rxCh
            steps.add { enableCccd(it, asyncCh, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
            steps.add { enableCccd(it, txCh, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) }
            steps.add {
                it.writeCharacteristic(rxCh, Zwift.RIDE_ON, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                ready = true
                listener.onConnected(firmware)
                listener.onStatus("Ready")
                false
            }
        }
        nextStep()
    }

    private fun enableCccd(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        g.setCharacteristicNotification(ch, true)
        val descriptor = ch.getDescriptor(Zwift.CCCD) ?: return false
        return g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    }

    private fun nextStep() {
        val g = gatt ?: return
        while (steps.isNotEmpty()) {
            val step = steps.removeFirst()
            if (step(g)) return
        }
    }

    // ---- Button frames --------------------------------------------------------------------

    private fun handleFrame(value: ByteArray) {
        if (value.isEmpty() || (value[0].toInt() and 0xFF) != Zwift.OP_CONTROLLER_NOTIFICATION) return
        val buttonMap = RideKeyPad.buttonMap(value, 1) ?: return
        val nowPressed = buttonMap.inv() and RideButton.ALL_MASK
        val newlyPressed = nowPressed and pressed.inv()
        pressed = nowPressed
        if (newlyPressed != 0) listener.onButtonsPressed(newlyPressed)
    }
}
