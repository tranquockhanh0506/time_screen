package com.example.app_limiter

import android.app.*
import android.app.usage.*
import android.content.*
import android.content.pm.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import androidx.annotation.NonNull
import kotlinx.coroutines.*
import android.Manifest
import java.util.Calendar
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding


/**
 * AppLimiterPlugin: Main plugin class that handles the communication between Flutter and Android
 * 
 * This plugin provides functionality for:
 * - Getting platform version
 * - Managing app usage permissions
 * - Blocking and unblocking apps
 * - Handling system overlay permissions
 * 
 * Implements:
 * - FlutterPlugin: For plugin registration and lifecycle
 * - MethodCallHandler: For handling method calls from Flutter
 * - ActivityAware: For accessing Activity context and permissions
 */
class AppLimiterPlugin: FlutterPlugin, MethodCallHandler,ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    // Coroutine scope for background tasks
    var job = Job()
    val scope = CoroutineScope(Dispatchers.Default + job)

    // Helper methods from MainActivity.kt
    /**
     * Checks if the app has permission to draw overlays
     * Required for displaying blocking UI on top of other apps
     * 
     * @param activity The current activity context
     * @return Boolean indicating if permission is granted
     */
    private fun checkDrawOverlayPermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true
        }
    }

    /**
     * Requests permission to draw overlays
     * Opens system settings if permission is not granted
     * 
     * @param activity The current activity context
     * @param requestCode Request code for permission result
     * @return Boolean indicating if permission was already granted
     */
    private fun requestDrawOverlayPermission(activity: Activity, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))
                activity.startActivityForResult(intent, requestCode)
                return false
            }
        }
        return true
    }

    /**
     * Checks if the app has permission to query all packages
     * Required for Android 11+ to access package information
     * 
     * @param context Application context
     * @return Boolean indicating if permission is granted
     */
    private fun checkQueryAllPackagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PackageManager.PERMISSION_GRANTED == context.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES)
        } else {
            true
        }
    }

    /**
     * Requests permission to query all packages
     * Required for Android 11+ functionality
     * 
     * @param activity The current activity context
     * @return Boolean indicating if permission was already granted
     */
    private fun requestQueryAllPackagesPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (activity.checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(arrayOf(Manifest.permission.QUERY_ALL_PACKAGES), 2)
                return false
            }
        }
        return true
    }

    /**
     * Checks if the app has usage stats permission
     * Required for monitoring app usage
     * 
     * @param context Application context
     * @return Boolean indicating if permission is granted
     */
    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Requests usage stats permission
     * Opens system settings for usage access
     * 
     * @param context Application context
     */
    private fun requestUsageStatsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(intent)
    }

    /**
     * Checks if a service is currently running
     * Used to monitor the blocking service status
     * 
     * @param serviceClassName The full class name of the service to check
     * @param context Application context
     * @return Boolean indicating if the service is running
     */
    private fun isServiceRunning(serviceClassName: String, context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClassName == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * Sets up an alarm for scheduled app blocking
     * Used to automatically start blocking at specific times
     * 
     * @param hour Hour of the day (24-hour format)
     * @param minute Minute of the hour
     * @param second Second of the minute
     */
    private fun setAlarm(hour: Int, minute: Int, second: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
        }
        // Use AlarmReceiver for broadcast pending intent
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, pendingIntentFlags)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    // Flutter plugin lifecycle methods
    /**
     * Called when the plugin is attached to the Flutter engine
     * Sets up the method channel and context
     */
    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "app_limiter")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
    }

    /**
     * Handles method calls from Flutter
     * Implements all the platform-specific functionality
     * 
     * Supported methods:
     * - getPlatformVersion: Returns Android version
     * - blockApp: Starts the app blocking service
     * - unblockApp: Stops the app blocking service
     * - checkPermission: Checks all required permissions
     * - requestAuthorization: Requests all required permissions
     */
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }

            "blockApp" -> {
                val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("Blocking", true).apply()
                val intent = Intent(context, BlockAppService::class.java)
                // Start the service safely and catch any runtime exceptions to avoid crashing
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    result.success(null)
                } catch (e: Exception) {
                    // If starting the service fails (permission/security exception etc.),
                    // clear the blocking flag and return an error to Flutter instead of crashing.
                    e.printStackTrace()
                    sharedPreferences.edit().putBoolean("Blocking", false).apply()
                    result.error("SERVICE_START_FAILED", e.message, null)
                }
            }

            "unblockApp" -> {
                val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPreferences.edit().putBoolean("Blocking", false).apply()
                val intent = Intent(context, BlockAppService::class.java)
                context.stopService(intent)
                result.success(null)
            }

            "checkPermission" -> {
                val hasOverlayPermission = activity?.let { checkDrawOverlayPermission(it) } ?: false
                val hasQueryPermission = activity?.let { requestQueryAllPackagesPermission(it) } ?: false
                val hasUsageStatsPermission = context.let { hasUsageStatsPermission(it) }

                if (hasOverlayPermission && hasQueryPermission && hasUsageStatsPermission) {
                    result.success("approved")
                } else {
                    result.success("denied")
                }
            }

            "requestAuthorization" -> {
    val currentActivity = activity
    if (currentActivity == null) {
        result.error("NO_ACTIVITY", "Activity is null", null)
        return
    }
    val hasOverlayPermission = checkDrawOverlayPermission(currentActivity)
    val hasQueryPermission = checkQueryAllPackagesPermission(context)
    val hasUsageStatsPermission = hasUsageStatsPermission(context)

    when {
        !hasOverlayPermission -> {
            requestDrawOverlayPermission(currentActivity, 1234)
            result.success("overlay_permission_requested")
        }
        !hasQueryPermission -> {
            requestQueryAllPackagesPermission(currentActivity)
            result.success("query_permission_requested")
        }
        !hasUsageStatsPermission -> {
            requestUsageStatsPermission(currentActivity)
            result.success("usage_stats_permission_requested")
        }
        else -> {
            result.success("all_permissions_granted")
        }
    }
}


            else -> result.notImplemented()
        }
    }

    /**
     * Called when the plugin is detached from the Flutter engine
     * Cleans up the method channel
     */
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    /**
     * Called when the plugin is attached to an Activity
     * Required for permission handling and UI interactions
     */
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }
    
    /**
     * Called when the plugin is detached from an Activity
     * Cleans up the activity reference
     */
    override fun onDetachedFromActivity() {
        activity = null
    }
    
    /**
     * Called when the plugin is reattached to an Activity after configuration changes
     * Updates the activity reference
     */
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }
    
    /**
     * Called when the plugin is detached from an Activity during configuration changes
     * Cleans up the activity reference
     */
    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }
    
}
