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
    // Ventana mínima que cubre el intervalo de polling (4 s x 3): solo se
    // necesita el ÚLTIMO evento RESUMED, no los 3 minutos completos.
    private const val QUERY_WINDOW_MS = 15_000L

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
     * Última app en primer plano mediante el ÚLTIMO evento RESUMED.
     *
     * Fix v1.5.4: la máquina de estados anterior anulaba el foreground con
     * PAUSED/STOPPED del paquete actual, dejando el estado "null" cuando el
     * launcher se pausaba sin que otra activity RESUMED aún. `isLauncherForeground`
     * interpretaba null como "mostrar pet", así que el pet aparecía sobre
     * cualquier ventana. Ahora el último RESUMED gana: PAUSED/STOPPED no
     * despejan el estado (no aportan info de qué app está en frente).
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
                }
            }
            current
        } catch (e: Exception) {
            Log.w(TAG, "Usage query failed")
            null
        }
    }

    /**
     * True si lo que el usuario ve como "frente" es el escritorio (lanzador por defecto).
     * Si el estado es desconocido, devuelve true (conservador: la mascota se muestra).
     */
    fun isLauncherForeground(context: Context): Boolean {
        if (!hasUsageAccess(context)) return true
        val launcherPkg = defaultLauncherPackage(context.packageManager) ?: return true
        val fg = queryLastForegroundPackage(context) ?: return true
        return fg == launcherPkg
    }
}
