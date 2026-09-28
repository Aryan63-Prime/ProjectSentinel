package com.sentinel.host.mdm

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
