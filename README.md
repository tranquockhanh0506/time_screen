# App Limiter Plugin

A Flutter plugin that allows developers to limit app usage and screen time on Android and iOS by blocking/unblocking apps and managing screen time permissions.

## 🧠 Features

- ✅ Get platform version
- ✅ Request and check Android permissions
- ✅ Block/unblock apps on Android
- ✅ Request iOS permissions
- ✅ Block/unblock apps on iOS

### 🔧 Available Methods

```dart
Future<void> initPlatformState()
Future<void> blockOrUnblocIosApp()
Future<bool> requestIosPermission()
Future<bool> checkAndroidPermission()
Future<void> requestAndroidPermission()
Future<void> blockAndroidApps()
Future<void> unBlockAndroidApps()
```

🪪 Permissions Required

🟢 Android
Add the following to your app’s AndroidManifest.xml file:
```
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="your.app.package.name"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission
            android:name="android.permission.PACKAGE_USAGE_STATS"
            tools:ignore="ProtectedPermissions" />

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" 
    tools:ignore="QueryAllPackagesPermission" />
</manifest>
```
Also in android/app/build.gradle.kts, set:
```
android {
    ndkVersion = "27.0.12077973"
}
```
🟣 iOS
Add the following to your Info.plist:
```
<key>com.apple.developer.device-activity-monitoring</key>
<true/>
<key>com.apple.developer.family-controls</key>
<true/>
```


```
Enable the Capability of Family Controll from Xcode:
```



📱 Platform Support

| Platform | Support |
| -------- | ------- |
| Android  | ✅       |
| iOS      | ✅       |
| Web      | ❌       |


🧪 Example Usage
```
final plugin = AppLimiterPlugin();

// Android permissions
await plugin.requestAndroidPermission();
await plugin.blockAndroidApps();
await plugin.unBlockAndroidApps();

// iOS permissions
await plugin.requestIosPermission();
await plugin.blockOrUnblocIosApp();
```
Check the full example in the /example directory.

🐞 Issues
Please report issues here:
https://github.com/connect-rizwan/AppLimiter-Plugin-Flutetr/issues