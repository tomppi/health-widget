package com.healthwidget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.healthwidget.databinding.ActivityMainBinding
import com.healthwidget.widget.HealthAppWidget
import com.healthwidget.widget.HealthWidgetUpdateWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var healthManager: HealthConnectManager

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthManager.permissions)) {
            lifecycleScope.launch { loadHealthData() }
        } else {
            Toast.makeText(this, "Both Health Connect permissions are required.", Toast.LENGTH_LONG).show()
            binding.tvStatus.text = "Permissions denied — tap Refresh to try again."
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startHealthWidgetService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        healthManager = HealthConnectManager(this)

        if (!healthManager.isAvailable()) {
            binding.tvStatus.text = "Health Connect is not installed on this device."
            binding.btnInstallHc.visibility = View.VISIBLE
            binding.btnInstallHc.setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                )
            }
            return
        }

        requestBatteryOptimisationExemption()
        requestNotificationPermissionAndStartService()

        binding.btnRefresh.setOnClickListener {
            lifecycleScope.launch { loadHealthData() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    checkPermissionsAndLoad()
                    delay(5 * 60 * 1000L)
                }
            }
        }

        HealthWidgetUpdateWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        if (::healthManager.isInitialized && healthManager.isAvailable()) {
            lifecycleScope.launch {
                if (healthManager.hasAllPermissions()) loadHealthData()
            }
        }
    }

    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startHealthWidgetService()
            } else {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startHealthWidgetService()
        }
    }

    private fun startHealthWidgetService() {
        val intent = Intent(this, HealthWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimisationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun checkPermissionsAndLoad() {
        lifecycleScope.launch {
            if (healthManager.hasAllPermissions()) {
                loadHealthData()
            } else {
                permissionLauncher.launch(healthManager.permissions)
            }
        }
    }

    private suspend fun loadHealthData() {
        binding.tvStatus.text = "Loading…"
        binding.progressBar.visibility = View.VISIBLE

        try {
            val data = healthManager.getLatestHealthData()

            binding.heartRateChart.heartRates = data.heartRates
            binding.sleepChart.sleepStages    = data.sleepStages
            binding.tvSleepDuration.text = data.sleepDurationMinutes?.let {
                val h = it / 60; val m = it % 60; "${h}h ${m}m total"
            } ?: "No sleep data"

            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            binding.tvStatus.text = "Last synced: ${fmt.format(Date())}"

            pushDataToWidgets(data)

        } catch (e: Exception) {
            binding.tvStatus.text = "Read error: ${e.localizedMessage}"
        } finally {
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun pushDataToWidgets(data: HealthData) {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, HealthAppWidget::class.java))
        ids.forEach { id ->
            HealthAppWidget.updateWidgetWithData(this, manager, id, data)
        }
    }
}
