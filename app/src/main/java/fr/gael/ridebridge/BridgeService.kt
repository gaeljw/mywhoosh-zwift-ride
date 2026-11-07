package fr.gael.ridebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager

/** Foreground service that keeps the controller connection alive while MyWhoosh is in front. */
class BridgeService : Service(), ZwiftRideClient.Listener {

    companion object {
        private const val CHANNEL = "bridge"
        private const val NOTIFICATION_ID = 1

        var isRunning = false
            private set

        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, BridgeService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, BridgeService::class.java)) }
    }

    private lateinit var prefs: Prefs
    private lateinit var client: ZwiftRideClient

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        client = ZwiftRideClient(this, this) { prefs.controller }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Controller bridge", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (!isRunning) {
            isRunning = true
            Hub.update(running = true)
            client.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.stop()
        isRunning = false
        Hub.learning = null
        Hub.update(status = "Stopped", running = false, connected = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Controller events ----------------------------------------------------------------

    override fun onStatus(text: String) {
        Hub.update(status = text)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onConnected(firmware: String?, address: String) {
        Hub.update(connected = true, firmware = firmware ?: "unknown")
        ControllerPin.toPin(prefs.controller, address)?.let {
            prefs.controller = it
            Hub.update(lastEvent = "Controller $it remembered")
        }
    }

    override fun onDisconnected() = Hub.update(connected = false)

    override fun onButtonsPressed(newlyPressed: Int) {
        Hub.learning?.let { dir ->
            val bit = newlyPressed.takeLowestOneBit()
            prefs.setMask(dir, bit)
            Hub.learning = null
            Hub.update(lastEvent = "Gear ${dir.name.lowercase()} is now: ${RideButton.labels(bit)}")
            return
        }
        when {
            (newlyPressed and prefs.mask(Direction.UP)) != 0 -> shift(Direction.UP)
            (newlyPressed and prefs.mask(Direction.DOWN)) != 0 -> shift(Direction.DOWN)
            else -> Hub.update(lastEvent = "Pressed ${RideButton.labels(newlyPressed)} (not mapped)")
        }
    }

    private fun shift(dir: Direction) {
        val name = "Gear ${dir.name.lowercase()}"
        val point = prefs.point(dir) ?: return Hub.update(lastEvent = "$name: tap point not calibrated yet")
        val tapper = TapService.instance ?: return Hub.update(lastEvent = "$name: accessibility service is off")
        tapper.blockedReason()?.let { return Hub.update(lastEvent = "$name: $it") }
        val ok = tapper.tap(point)
        Hub.update(lastEvent = if (ok) name else "$name: tap rejected by Android")
        when (prefs.feedback) {
            Feedback.OFF -> Unit
            Feedback.CONTROLLER -> client.vibrate()
            Feedback.PHONE -> getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("RideBridge")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
