package io.github.desktopmodelauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent

class AccessibilityActionService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        serviceInstance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceInstance === this) serviceInstance = null
    }

    companion object {
        private var serviceInstance: AccessibilityActionService? = null

        fun triggerBack() {
            serviceInstance?.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AccessibilityActionService::class.java).flattenToString()
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
