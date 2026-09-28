# Feature Specification & Code: Enterprise Device Owner & MDM Integration

## 1. Overview & Problem Statement
On standard Android operating systems, user-installed applications can be force-stopped, uninstalled, or restricted by device users. In fleet management, lone-worker security, and enterprise kiosk deployments, organizations require **tamper-proof protection**, silent background management, and remote device wiping without interactive user prompts.

This feature integrates Android's official **Enterprise Device Policy Architecture** (`DevicePolicyManager`). When provisioned as a **Device Owner** (via QR code provisioning during initial setup or via ADB `dpm set-device-owner`), the host application gains hardware-level administrative control.

### Capabilities Unlocked in Device Owner Mode:
1. **Tamper Proofing:** Block user uninstallation and app disabling.
2. **Silent Permission Auto-Granting:** Automatically grant all runtime permissions (Camera, Location, Mic) without showing interactive prompts to the user.
3. **Remote Screen Lock & Factory Wipe:** Remotely lock the device or wipe all data if stolen.
4. **Kiosk / Lock Task Mode:** Lock the device into specific allowed enterprise applications.

---

## 2. Manifest & Policy Configuration (`host-app/`)

### File: `host-app/app/src/main/res/xml/device_admin_policy.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
        <expire-password />
        <encrypted-storage />
        <disable-camera />
        <disable-keyguard-features />
    </uses-policies>
</device-admin>
```

### Manifest Declaration in `host-app/app/src/main/AndroidManifest.xml`:

```xml
<!-- Device Admin Receiver -->
<receiver
    android:name=".receiver.SentinelDeviceAdminReceiver"
    android:label="Sentinel Device Manager"
    android:description="@string/device_admin_description"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_policy" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
        <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
    </intent-filter>
</receiver>
```

---

## 3. Host-App Implementation

### File: `host-app/app/src/main/java/com/sentinel/host/receiver/SentinelDeviceAdminReceiver.kt`

```kotlin
package com.sentinel.host.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class SentinelDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "Sentinel:DeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Sentinel Device Admin enabled successfully")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling Sentinel Device Management will disable enterprise fleet tracking and security policies."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Sentinel Device Admin disabled")
    }
}
```

### File: `host-app/data/src/main/java/com/sentinel/host/data/device/MdmManager.kt`

```kotlin
package com.sentinel.host.data.device

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.sentinel.host.receiver.SentinelDeviceAdminReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MdmManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sentinel:MDM"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, SentinelDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isProfileOwner: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dpm.isProfileOwnerApp(context.packageName)
        } else false

    val isDeviceAdminActive: Boolean
        get() = dpm.isAdminActive(adminComponent)

    /**
     * Prevents the user from uninstalling the Sentinel host app.
     */
    fun setUninstallProtection(enabled: Boolean): Boolean {
        if (!isDeviceOwner) {
            Log.w(TAG, "Cannot block uninstall: Not a Device Owner")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setUninstallBlocked(adminComponent, context.packageName, enabled)
                Log.i(TAG, "Uninstall blocked set to: $enabled")
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting uninstall protection: ${e.message}", e)
            false
        }
    }

    /**
     * Silently auto-grants all runtime permissions without showing prompts to the user.
     */
    fun autoGrantAllPermissions(): Boolean {
        if (!isDeviceOwner && !isProfileOwner) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        val permissionsToGrant = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        )

        var allSuccess = true
        for (perm in permissionsToGrant) {
            try {
                dpm.setPermissionGrantState(
                    adminComponent,
                    context.packageName,
                    perm,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
                Log.i(TAG, "Auto-granted runtime permission: $perm")
            } catch (e: Exception) {
                Log.e(TAG, "Failed auto-granting $perm: ${e.message}")
                allSuccess = false
            }
        }
        return allSuccess
    }

    /**
     * Remotely locks the device screen immediately.
     */
    fun lockDeviceNow(): Boolean {
        if (!isDeviceAdminActive) return false
        return try {
            dpm.lockNow()
            Log.i(TAG, "Device locked remotely via MDM")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock device: ${e.message}")
            false
        }
    }

    /**
     * Remotely triggers an enterprise factory reset / wipe in case of theft.
     */
    fun remoteFactoryWipe(): Boolean {
        if (!isDeviceOwner) return false
        return try {
            Log.w(TAG, "Executing remote factory wipe!")
            dpm.wipeData(0)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute wipeData: ${e.message}")
            false
        }
    }
}
```

---

## 4. Provisioning Device Owner Mode

To set the host app as a Device Owner on unmanaged devices during initial deployment or testing:

```bash
# Ensure no accounts (Google, etc.) are currently logged into the device
adb shell dpm set-device-owner com.sentinel.host/.receiver.SentinelDeviceAdminReceiver
```
