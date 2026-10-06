package com.clairjour.app.shield

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object ShieldServiceStatus {

    /** True when the user has switched the shield on in the Android accessibility settings. */
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, InstagramShieldService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
