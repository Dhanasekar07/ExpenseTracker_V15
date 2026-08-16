package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AlertDialog

class PermissionsActivity : AppCompatActivity() {

    companion object {
        private const val SMS_REQUEST_CODE = 1001
    }

    // Which permission we're waiting for after returning from Settings
    private var waitingFor = ""

    private lateinit var checkSms: ImageView
    private lateinit var checkNotif: ImageView
    private lateinit var checkOverlay: ImageView
    private lateinit var checkBattery: ImageView
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        checkSms     = findViewById(R.id.checkSms)
        checkNotif   = findViewById(R.id.checkNotif)
        checkOverlay = findViewById(R.id.checkOverlay)
        checkBattery = findViewById(R.id.checkBattery)
        btnContinue  = findViewById(R.id.btnContinue)

        // Set click listeners on each row for manual re-grant
        findViewById<LinearLayout>(R.id.permSms).setOnClickListener { requestSms() }
        findViewById<LinearLayout>(R.id.permNotif).setOnClickListener { requestNotifListener() }
        findViewById<LinearLayout>(R.id.permOverlay).setOnClickListener { requestOverlay() }
        findViewById<LinearLayout>(R.id.permBattery).setOnClickListener { requestBattery() }

        btnContinue.setOnClickListener { startSequentialGrant() }

        refreshChecks()
    }

    override fun onResume() {
        super.onResume()

        // Handle return from Settings pages
        when (waitingFor) {
            "sms_settings" -> {
                waitingFor = ""
                refreshChecks()
                if (isSmsGranted()) proceedAfterSms()
                else showSmsSettingsDialog()
            }
            "notif" -> {
                waitingFor = ""
                refreshChecks()
                if (isNotifGranted()) proceedAfterNotif()
            }
            "overlay" -> {
                waitingFor = ""
                refreshChecks()
                if (isOverlayGranted()) proceedAfterOverlay()
            }
            "battery" -> {
                waitingFor = ""
                refreshChecks()
                if (isBatteryExempt()) proceedAfterBattery()
            }
            else -> refreshChecks()
        }
    }

    // ── Sequential Grant Flow ───────────────────────────────────────────

    private fun startSequentialGrant() {
        when {
            !isSmsGranted()     -> requestSms()
            !isNotifGranted()   -> requestNotifListener()
            !isOverlayGranted() -> requestOverlay()
            !isBatteryExempt()  -> requestBattery()
            else                -> goToMain()
        }
    }

    // ── SMS (runtime dialog — seamless) ─────────────────────────────────

    private var smsDeniedOnce = false

    private fun requestSms() {
        if (isSmsGranted()) {
            refreshChecks()
            proceedAfterSms()
            return
        }
        // Try system popup first
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            SMS_REQUEST_CODE
        )
        
        // Sideloaded apps can't get SMS popup — go to Settings - OLD CODE
        // waitingFor = "sms_settings"
        // AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
        //     .setTitle("SMS Permission")
        //     .setMessage("Allow Expense Tracker to read SMS messages to detect payment transactions.")
        //     .setCancelable(false)
        //     .setPositiveButton("Go to Settings") { _, _ ->
        //         startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        //             data = Uri.parse("package:$packageName")
        //         })
        //     }
        //     .show()

        // // If already denied once or permanently denied, go to app settings
        // if (!ActivityCompat.shouldShowRequestPermissionRationale(
        //         this, Manifest.permission.READ_SMS)) {
        //     waitingFor = "sms_settings"
        //     startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        //         data = Uri.parse("package:$packageName")
        //     })
        //     return
        // }

        // ActivityCompat.requestPermissions(
        //     this,
        //     arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
        //     SMS_REQUEST_CODE
        // )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_REQUEST_CODE) {
            refreshChecks()
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                proceedAfterSms()
            } else {
                showSmsSettingsDialog()
            }
        }
    }

    private fun showSmsSettingsDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("SMS Permission")
            .setMessage("Allow Expense Tracker to read SMS messages to detect payment transactions.")
            .setCancelable(false)
            .setPositiveButton("Go to Settings") { _, _ ->
                waitingFor = "sms_settings"
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .show()
    }

    private fun proceedAfterSms() {
        if (!isOverlayGranted()) requestOverlay()
        else if (!isBatteryExempt()) requestBattery()
        else goToMain()
    }

    // ── Notification Listener (opens Settings) ──────────────────────────

    private fun requestNotifListener() {
        if (isNotifGranted()) {
            refreshChecks()
            proceedAfterNotif()
            return
        }
        waitingFor = "notif"
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun proceedAfterNotif() {
        if (!isOverlayGranted()) requestOverlay()
        else if (!isBatteryExempt()) requestBattery()
        else goToMain()
    }

    // ── Overlay (opens Settings) ────────────────────────────────────────

    private fun requestOverlay() {
        if (isOverlayGranted()) {
            refreshChecks()
            proceedAfterOverlay()
            return
        }
        waitingFor = "overlay"
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ))
    }

    private fun proceedAfterOverlay() {
        if (!isBatteryExempt()) requestBattery()
        else goToMain()
    }

    // ── Battery Optimization (system dialog) ────────────────────────────

    private fun requestBattery() {
        if (isBatteryExempt()) {
            refreshChecks()
            proceedAfterBattery()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
            .setTitle("Battery Optimization")
            .setMessage("Allow Expense Tracker to run in the background to keep tracking payments.")
            .setCancelable(false)
            .setPositiveButton("Allow") { _, _ ->
                waitingFor = "battery"
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) {
                    waitingFor = ""
                    proceedAfterBattery()
                }
            }
            .show()
            
        //    Commented Older Code
        // waitingFor = "battery"
        // try {
        //     startActivity(Intent(
        //         Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        //         Uri.parse("package:$packageName")
        //     ))
        // } catch (e: Exception) {
        //     // Some devices don't support this intent
        //     waitingFor = ""
        //     proceedAfterBattery()
        // }
    }

    private fun proceedAfterBattery() {
        refreshChecks()
        if (allGranted()) goToMain()
    }

    // ── Permission Checks ───────────────────────────────────────────────

    private fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isNotifGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun isBatteryExempt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun allGranted(): Boolean =
        isSmsGranted() && isNotifGranted() && isOverlayGranted() && isBatteryExempt()

    // ── UI Updates ──────────────────────────────────────────────────────

    private fun refreshChecks() {
        setCheck(checkSms, isSmsGranted())
        setCheck(checkNotif, isNotifGranted())
        setCheck(checkOverlay, isOverlayGranted())
        setCheck(checkBattery, isBatteryExempt())

        if (allGranted()) {
            btnContinue.text = "Continue"
        }
    }

    private fun setCheck(icon: ImageView, granted: Boolean) {
        icon.setImageResource(
            if (granted) R.drawable.ic_check_green
            else R.drawable.ic_check_grey
        )
    }

    // ── Navigation ──────────────────────────────────────────────────────

    private fun goToMain() {
        AppPreferences.setOnboarded(this)

        try { CategoryManager.initialize(this) } catch (e: Exception) { e.printStackTrace() }

        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
