package fr.gael.ridebridge

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bluetoothPermissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var startButton: Button
    private lateinit var learnUp: Button
    private lateinit var learnDown: Button
    private lateinit var controllerView: TextView
    private lateinit var pointsView: TextView
    private lateinit var checklistView: TextView
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun section(title: String) = col.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 4)
        })
        fun text() = TextView(this).also { col.addView(it) }
        fun button(label: String, onClick: () -> Unit) =
            Button(this).apply { text = label; setOnClickListener { onClick() } }.also { col.addView(it) }

        section("Status")
        statusView = text()
        startButton = button("Start") { toggleBridge() }

        section("Shift buttons")
        learnUp = button("") { learn(Direction.UP) }
        learnDown = button("") { learn(Direction.DOWN) }

        section("Controller")
        controllerView = text()
        button("Forget controller") { forgetController() }

        section("Tap points on MyWhoosh")
        pointsView = text()
        button("Calibrate (overlay appears after 5 s)") { calibrate() }

        section("Feedback on shift")
        col.addView(RadioGroup(this).apply {
            for (f in Feedback.entries) {
                addView(RadioButton(this@MainActivity).apply {
                    id = View.generateViewId()
                    text = f.label
                    isChecked = prefs.feedback == f
                    setOnClickListener { prefs.feedback = f }
                })
            }
        })

        section("Phone setup")
        checklistView = text()
        button("1. Accessibility settings → RideBridge gear taps") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("2. App info (⋮ → Allow restricted settings, if Android blocks step 1)") { openAppInfo() }
        button("3. Xiaomi autostart") { openXiaomiAutostart() }
        button("4. Battery: no restrictions") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        val scroll = ScrollView(this).apply { addView(col) }
        // Android 15+ draws apps edge to edge: keep content clear of the system bars.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onStart() {
        super.onStart()
        Hub.addListener(listener)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onStop() {
        Hub.removeListener(listener)
        super.onStop()
    }

    // ---- Actions --------------------------------------------------------------------------

    private fun toggleBridge() {
        if (BridgeService.isRunning) {
            BridgeService.stop(this)
            return
        }
        val missing = (bluetoothPermissions + Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) BridgeService.start(this) else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Notifications are optional; Bluetooth is not.
        if (bluetoothPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            BridgeService.start(this)
        } else {
            toast("RideBridge needs the Nearby devices permission to talk to the controller")
        }
        render()
    }

    private fun learn(dir: Direction) {
        if (!Hub.connected) return toast("Connect the controller first")
        Hub.learning = if (Hub.learning == dir) null else dir
    }

    private fun forgetController() {
        prefs.controller = null
        render()
        toast(if (BridgeService.isRunning) "Stop and start the bridge to pair with another controller" else "Controller forgotten")
    }

    private fun calibrate() {
        val service = TapService.instance ?: return toast("Turn on the accessibility service first (setup step 1)")
        service.startCalibration(5_000)
        toast("Switch to MyWhoosh now, in landscape")
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openXiaomiAutostart() {
        val intent = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        runCatching { startActivity(intent) }.onFailure { openAppInfo() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---- Rendering ------------------------------------------------------------------------

    private fun render() {
        statusView.text = buildString {
            appendLine(Hub.status)
            appendLine("Controller: " + if (Hub.connected) "connected (firmware ${Hub.firmware})" else "not connected")
            if (Hub.lastEvent.isNotEmpty()) append("Last: ${Hub.lastEvent}")
        }
        startButton.text = if (Hub.running || BridgeService.isRunning) "Stop" else "Start"

        fun learnLabel(dir: Direction) =
            if (Hub.learning == dir) "Press the button to use for gear ${dir.name.lowercase()}… (tap to cancel)"
            else "Gear ${dir.name.lowercase()}: ${RideButton.labels(prefs.mask(dir))} (tap to change)"
        learnUp.text = learnLabel(Direction.UP)
        learnDown.text = learnLabel(Direction.DOWN)

        controllerView.text = prefs.controller?.let { "Only connects to $it" }
            ?: "Not paired yet: the first Zwift Ride found will be remembered"

        pointsView.text = Direction.entries.joinToString("\n") { dir ->
            val p = prefs.point(dir)
            "Gear ${dir.name.lowercase()}: " + if (p == null) "not set" else "x=${p.x.toInt()}, y=${p.y.toInt()}"
        }

        val btOk = bluetoothPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val battOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        checklistView.text = buildString {
            appendLine("${mark(btOk)} Nearby devices permission (asked on Start)")
            appendLine("${mark(TapService.instance != null)} Accessibility service on")
            appendLine("${mark(battOk)} Battery optimisation off")
            append("❔ Xiaomi autostart (can't be checked from the app)")
        }
    }
}
