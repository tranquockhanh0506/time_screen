package com.example.app_limiter

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

const val CHANNEL_ID = "BlockAppService_Channel_ID"
const val NOTIFICATION_ID = 1

class BlockAppService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val CHANNEL = "flutter_screentime"
    private var isOverlayDisplayed = false
    private val userApps = ArrayList<ResolveInfo>()
    private var handler: Handler? = null
    private var blockingRunnable: Runnable? = null
    private var currentForegroundApp: String? = null

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    fun isDeviceLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }

    fun getCurrentForegroundApp(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 1000 * 10 // Last 10 seconds for more recent data

        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        var lastEvent: UsageEvents.Event? = null
        
        while (usageEvents.hasNextEvent()) {
            val event = UsageEvents.Event()
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastEvent = event
            }
        }
        
        return lastEvent?.packageName
    }

    fun isBlockedAppInForeground(context: Context): Boolean {
        val foregroundApp = getCurrentForegroundApp(context)
        
        // Update current foreground app
        currentForegroundApp = foregroundApp
        
        if (foregroundApp != null) {
            for (app in userApps) {
                if (app.activityInfo.packageName == foregroundApp) {
                    return true
                }
            }
        }
        return false
    }

    fun isLauncherApp(resolveInfo: ResolveInfo, context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val defaultLauncher = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo.activityInfo.packageName == defaultLauncher?.activityInfo?.packageName
    }

    private fun showOverlay() {
        try {
            // Double-check overlay permission before adding the view
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                println("[WARN] showOverlay called but no overlay permission")
                return
            }

            if (!isOverlayDisplayed && overlayView?.windowToken == null) {
                windowManager?.addView(overlayView, params)
                isOverlayDisplayed = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideOverlay() {
        try {
            if (isOverlayDisplayed && overlayView?.windowToken != null) {
                windowManager?.removeView(overlayView)
                isOverlayDisplayed = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun blockApps() {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        userApps.clear()
        // Filter out system apps
        for (app in apps) {
            // Exclude system apps
            if ((app.activityInfo.packageName == "com.android.chrome") ||
                ((app.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                !app.activityInfo.name.contains("com.android.launcher") &&
                !isLauncherApp(app, this) &&
                app.activityInfo.packageName != this.packageName)
            ) {
                userApps.add(app)
            }
        }
        
        startBlockingLoop()
    }

    private fun startBlockingLoop() {
        handler = Handler(Looper.getMainLooper())
        
        blockingRunnable = object : Runnable {
            override fun run() {
                val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val shouldBlock = sharedPreferences.getBoolean("Blocking", false)
                
                // Check permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this@BlockAppService) || !hasUsageStatsPermission(this@BlockAppService)) {
                        val editor = sharedPreferences.edit()
                        editor.putBoolean("Blocking", false)
                        editor.apply()
                        hideOverlay()
                        return
                    }
                }
                
                if (!shouldBlock) {
                    hideOverlay()
                    return
                }
                
                val isDeviceLocked = isDeviceLocked(this@BlockAppService)
                val isBlockedAppActive = isBlockedAppInForeground(this@BlockAppService)
                
                when {
                    isDeviceLocked -> {
                        // Device is locked, hide overlay
                        hideOverlay()
                    }
                    isBlockedAppActive -> {
                        // Blocked app is active, show overlay
                        showOverlay()
                    }
                    else -> {
                        // No blocked app is active, hide overlay
                        hideOverlay()
                    }
                }
                
                // Continue the loop with reduced delay for better responsiveness
                handler?.postDelayed(this, 200)
            }
        }
        
        handler?.post(blockingRunnable!!)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("[DEBUG] onStartCommand()")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // If we don't have overlay permission, avoid inflating/adding the overlay view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            println("[WARN] No overlay permission - stopping service and disabling blocking flag")
            val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("Blocking", false).apply()
            // Stop the service to avoid crashes when trying to add a view without permission
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.block_overlay, null)
        } catch (e: Exception) {
            e.printStackTrace()
            val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("Blocking", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "BlockAppService Channel", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockAppService")
            .setContentText("Service is running...")
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // If we cannot start foreground (e.g., MissingForegroundServiceTypeException),
            // clear Blocking flag and stop the service to avoid crashing the app.
            e.printStackTrace()
            val sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("Blocking", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        blockApps()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        handler?.removeCallbacks(blockingRunnable!!)
        handler = null
        blockingRunnable = null
        
        // Hide overlay if it's showing
        hideOverlay()
        
        println("[DEBUG] onDestroy()")
    }
}