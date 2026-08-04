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
    private const val QUERY_WINDOW_MS = 180_000L

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

    /**
     * Última app que pasó a primer plano en la ventana reciente de eventos.
     */
    @Suppress("DEPRECATION")
    private fun queryLastForegroundPackage(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - QUERY_WINDOW_MS
            val events = usm.queryEvents(begin, end)
            val ev = UsageEvents.Event()
            var last: String? = null
            var sawEvent = false
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                sawEvent = true
                when {
                    ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND -> last = ev.packageName
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED -> last = ev.packageName
                }
            }
            if (!sawEvent) null else last
        } catch (e: Exception) {
            Log.w(TAG, "Usage query failed")
            null
        }
    }

    /**
     * True si lo que el usuario ve como “frente” es el escritorio (lanzador por defecto).
     */
    fun isLauncherForeground(context: Context): Boolean {
        if (!hasUsageAccess(context)) return true
        val launcherPkg = defaultLauncherPackage(context.packageManager) ?: return true
        val fg = queryLastForegroundPackage(context) ?: return false
        return fg == launcherPkg
    }
}
