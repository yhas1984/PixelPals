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
     * Última app en primer plano mediante máquina de estados sobre el stream de eventos.
     * Los pares resume/stop cancelan estados obsoletos; así un "resume" repetido de un
     * overlay del sistema (p. ej. la pantalla asistente de ColorOS) no queda clavado.
     */
    @Suppress("DEPRECATION")
    private fun queryLastForegroundPackage(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - QUERY_WINDOW_MS
            val events = usm.queryEvents(begin, end)
            val ev = UsageEvents.Event()
            var current: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                when (ev.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> current = ev.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    EVENT_ACTIVITY_DESTROYED,
                    EVENT_ACTIVITY_STOPPED_NEW -> if (ev.packageName == current) current = null
                }
            }
            current
        } catch (e: Exception) {
            Log.w(TAG, "Usage query failed")
            null
        }
    }

    /**
     * True si lo que el usuario ve como “frente” es el escritorio (lanzador por defecto).
     * Si el estado es desconocido, devuelve true (conservador: la mascota se muestra).
     */
    fun isLauncherForeground(context: Context): Boolean {
        if (!hasUsageAccess(context)) return true
        val launcherPkg = defaultLauncherPackage(context.packageManager) ?: return true
        val fg = queryLastForegroundPackage(context) ?: return true
        return fg == launcherPkg
    }

    private const val EVENT_ACTIVITY_STOPPED_NEW = 23
    private const val EVENT_ACTIVITY_DESTROYED = 13
}
