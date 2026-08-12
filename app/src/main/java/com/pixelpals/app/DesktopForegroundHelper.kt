package com.pixelpals.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * Detecta si el lanzador (escritorio) está en primer plano usando estadísticas de uso.
 * Sin acceso concedido en Ajustes, [isLauncherForeground] devuelve true (comportamiento anterior).
 */
object DesktopForegroundHelper {
    private const val TAG = "DesktopForegroundHelper"
    private const val INITIAL_QUERY_WINDOW_MS = 24 * 60 * 60 * 1_000L
    private const val QUERY_WINDOW_MS = 60_000L
    private var hasPerformedInitialQuery = false
    private var lastForegroundPackage: String? = null
    private var lastUsageAccess: Boolean? = null

    internal data class ForegroundEvent(
        val packageName: String,
        val eventType: Int,
    )

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun defaultLauncherPackage(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName
    }

    @Suppress("DEPRECATION")
    internal fun resolveLatestForegroundPackage(
        events: Iterable<ForegroundEvent>,
        fallback: String?,
    ): String? {
        var current = fallback
        events.forEach { event ->
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> current = event.packageName
            }
        }
        return current
    }

    @Suppress("DEPRECATION")
    private fun queryLastForegroundPackage(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val queryWindow = if (hasPerformedInitialQuery) QUERY_WINDOW_MS else INITIAL_QUERY_WINDOW_MS
            hasPerformedInitialQuery = true
            val events = usm.queryEvents(end - queryWindow, end)
            val ev = UsageEvents.Event()
            var current = lastForegroundPackage
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    current = ev.packageName
                }
            }
            lastForegroundPackage = current
            current
        } catch (e: Exception) {
            Log.w(TAG, "Usage query failed", e)
            lastForegroundPackage
        }
    }

    /**
     * True si lo que el usuario ve como "frente" es el escritorio (lanzador por defecto).
     * Con acceso de uso, un estado desconocido oculta la mascota para no cubrir otra app.
     */
    fun isLauncherForeground(context: Context): Boolean {
        val usageAccess = hasUsageAccess(context)
        if (!usageAccess) {
            hasPerformedInitialQuery = false
            lastForegroundPackage = null
            lastUsageAccess = false
            return true
        }
        if (lastUsageAccess == false) {
            hasPerformedInitialQuery = false
            lastForegroundPackage = null
        }
        lastUsageAccess = true
        val launcherPkg = defaultLauncherPackage(context.packageManager) ?: return false
        val fg = queryLastForegroundPackage(context) ?: return false
        return fg == launcherPkg
    }
}
