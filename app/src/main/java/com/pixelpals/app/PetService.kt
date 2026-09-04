package com.pixelpals.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import com.pixelpals.app.data.prefs.SelectedPetStore
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import com.pixelpals.app.core.domain.PetType
import android.os.Looper
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pixelpals.app.status.PetDashboardActivity
import com.pixelpals.app.feature.overlay.behavior.TelaCornerWebState
import com.pixelpals.app.feature.overlay.behavior.TelaSilkState
import com.pixelpals.app.notifications.PetCareNotificationManager
import com.pixelpals.app.notifications.PetCareNotificationScheduler
import com.pixelpals.app.core.services.AppServices
import com.pixelpals.app.feature.care.CorgiCareCloud
import com.pixelpals.app.feature.care.CorgiFetchBallOverlay
import com.pixelpals.app.feature.care.CarePoseLoader
import com.pixelpals.app.feature.care.DesktopCarePlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PetService : Service() {

    companion object {
        private const val TAG = "PetService"
        @Volatile
        var isRunning: Boolean = false
        const val CHANNEL_ID = "pixelpals_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_HIDE = "com.pixelpals.app.ACTION_HIDE"
        const val ACTION_SHOW = "com.pixelpals.app.ACTION_SHOW"
        const val ACTION_STOP = "com.pixelpals.app.ACTION_STOP"
        const val ACTION_CONSUME_TREASURE = "com.pixelpals.app.ACTION_CONSUME_TREASURE"
        const val ACTION_REFRESH_PET = "com.pixelpals.app.ACTION_REFRESH_PET"
        const val ACTION_DEBUG_TELA_WEB = "com.pixelpals.app.ACTION_DEBUG_TELA_WEB"
        const val ACTION_DEBUG_TELA_CORNER_WEB = "com.pixelpals.app.ACTION_DEBUG_TELA_CORNER_WEB"
        const val EXTRA_PET_TYPE = "pet_type"
        private const val EXTRA_REFRESH_MESSAGE = "REFRESH_MESSAGE"
        private const val EXTRA_REFRESH_CELEBRATE = "REFRESH_CELEBRATE"
        private const val PET_SIZE_DP = 80
        /** Tope defensivo: el view (2x el sprite) nunca supera este % del ancho de pantalla. */
        private const val MAX_VIEW_SIZE_RATIO = 0.40f
        private const val HOME_POLL_INTERVAL_MS = 4_000L
        private const val HOME_POLL_INTERVAL_SLOW_MS = 60_000L

        fun requestPetRefresh(context: Context, message: String? = null, celebrate: Boolean = false) {
            if (!isRunning) return
            val intent = Intent(context, PetService::class.java).apply {
                action = ACTION_REFRESH_PET
                putExtra(EXTRA_REFRESH_MESSAGE, message)
                putExtra(EXTRA_REFRESH_CELEBRATE, celebrate)
            }
            context.startService(intent)
        }

        fun requestPetChange(context: Context, petType: PetType) {
            val selectedPetStore = SelectedPetStore(context)
            selectedPetStore.save(petType)
            selectedPetStore.setPetEnabled(true)
            val intent = Intent(context, PetService::class.java).apply {
                putExtra(EXTRA_PET_TYPE, petType.name)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure {
                    selectedPetStore.setPetEnabled(false)
                    Log.w(TAG, "Pet service start failed", it)
                }
            PetCareNotificationScheduler.schedule(context)
        }

        fun stopPet(context: Context) {
            SelectedPetStore(context).setPetEnabled(false)
            PetCareNotificationScheduler.cancel(context)
            context.stopService(Intent(context, PetService::class.java))
        }

        fun requestTreasureReactionIfRunning(context: Context, emoji: String) {
            if (!isRunning) return
            val intent = Intent(context, PetService::class.java).apply {
                action = ACTION_CONSUME_TREASURE
                putExtra("TREASURE_EMOJI", emoji)
            }
            context.startService(intent)
        }

        fun requestTelaWebTest(context: Context) {
            val selectedPetStore = SelectedPetStore(context)
            selectedPetStore.save(PetType.TELA)
            selectedPetStore.setPetEnabled(true)
            val intent = Intent(context, PetService::class.java).apply {
                action = ACTION_DEBUG_TELA_WEB
                putExtra(EXTRA_PET_TYPE, PetType.TELA.name)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Tela web test start failed", it) }
        }

        fun requestTelaCornerWebTest(context: Context) {
            val selectedPetStore = SelectedPetStore(context)
            selectedPetStore.save(PetType.TELA)
            selectedPetStore.setPetEnabled(true)
            val intent = Intent(context, PetService::class.java).apply {
                action = ACTION_DEBUG_TELA_CORNER_WEB
                putExtra(EXTRA_PET_TYPE, PetType.TELA.name)
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Tela corner web test start failed", it) }
        }

        fun refreshNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private var windowManager: WindowManager? = null
    private var petView: PetView? = null
    private var careOverlay: CorgiCareCloud? = null
    private var fetchBall: CorgiFetchBallOverlay? = null
    private val careScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isCareRoomVisible: Boolean = false
    private var isScreenOn: Boolean = true
    private var telaWebOverlay: TelaWebOverlayController? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var airplaneReceiver: BroadcastReceiver? = null
    private var isViewAttached = false
    private var currentPetType: PetType = PetType.CORGI
    private var isForegroundStarted = false
    private lateinit var selectedPetStore: SelectedPetStore

    private val homeCheckHandler = Handler(Looper.getMainLooper())
    private var userManuallyHidden = false
    private var lastAppliedPetVisible: Boolean? = null

    private val baseOverlayFlags =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private val homeCheckRunnable = object : Runnable {
        override fun run() {
            try {
                if (!isViewAttached) {
                    homeCheckHandler.postDelayed(this, HOME_POLL_INTERVAL_SLOW_MS)
                    return
                }
                refreshKeyboardVisibility()
                refreshPetVisibilityForForeground()
                homeCheckHandler.postDelayed(this, HOME_POLL_INTERVAL_MS)
            } catch (e: Exception) {
                Log.w(TAG, "Polling cycle failed; keeping service alive", e)
                homeCheckHandler.postDelayed(this, HOME_POLL_INTERVAL_MS)
            }
        }
    }

    private var lastImeVisible: Boolean? = null

    private fun refreshKeyboardVisibility() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val wm = windowManager ?: return
        val metrics = runCatching { wm.currentWindowMetrics }.getOrNull() ?: return
        val insets = metrics.windowInsets
        val visible = insets.isVisible(WindowInsets.Type.ime())
        if (visible == lastImeVisible) return
        lastImeVisible = visible
        if (visible) careOverlay?.close()
        val height = insets.getInsets(WindowInsets.Type.ime()).bottom
        petView?.onKeyboardChanged(visible, height)
        if (!visible) {
            // Re-afirma la posición tras cerrar el teclado por si el sistema re-layout la ventana.
            val view = petView ?: return
            val params = view.getWindowParams() ?: return
            runCatching { view.updateWindowLayout(params) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        selectedPetStore = SelectedPetStore(this)
        currentPetType = selectedPetStore.load()
        if (BuildConfig.CARE_SCENES_ENABLED) careScope.launch {
            AppServices.careScenes(this@PetService).roomOwners.collect { owners ->
                isCareRoomVisible = owners.isNotEmpty()
                if (isCareRoomVisible) careOverlay?.close()
                applyPetOverlayVisible(shouldShowPetForPolicy())
            }
        }
        createNotificationChannel()
        PetCareNotificationManager.createChannel(this)
        if (selectedPetStore.isPetEnabled()) PetCareNotificationScheduler.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DEBUG_TELA_WEB || action == ACTION_DEBUG_TELA_CORNER_WEB) {
            currentPetType = PetType.TELA
            selectedPetStore.save(currentPetType)
            selectedPetStore.setPetEnabled(true)
        }
        if (action == ACTION_SHOW) selectedPetStore.setPetEnabled(true)
        val shouldStartForeground = when (action) {
            ACTION_STOP -> false
            ACTION_SHOW -> true
            ACTION_HIDE,
            ACTION_CONSUME_TREASURE,
            ACTION_REFRESH_PET -> selectedPetStore.isPetEnabled()
            else -> selectedPetStore.isPetEnabled()
        }
        if (shouldStartForeground) {
            ensureForeground()
        }

        when (intent?.action) {
            ACTION_HIDE -> {
                if (!selectedPetStore.isPetEnabled()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                userManuallyHidden = true
                applyPetOverlayVisible(false)
                updateNotification(true)
                return START_NOT_STICKY
            }
            ACTION_SHOW -> {
                userManuallyHidden = false
                ensureOverlayReady()
                applyPetOverlayVisible(shouldShowPetForPolicy())
                updateNotification(false)
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                selectedPetStore.setPetEnabled(false)
                PetCareNotificationScheduler.cancel(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONSUME_TREASURE -> {
                if (!selectedPetStore.isPetEnabled()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureOverlayReady()
                val emoji = intent.getStringExtra("TREASURE_EMOJI") ?: "✨"
                petView?.consumeTreasure(emoji)
                return START_NOT_STICKY
            }
            ACTION_REFRESH_PET -> {
                if (!selectedPetStore.isPetEnabled()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureOverlayReady()
                petView?.refreshFromRepository(
                    message = intent.getStringExtra(EXTRA_REFRESH_MESSAGE),
                    celebrate = intent.getBooleanExtra(EXTRA_REFRESH_CELEBRATE, false),
                )
                return START_NOT_STICKY
            }
        }

        val petTypeName = intent?.getStringExtra(EXTRA_PET_TYPE)
        if (petTypeName != null) {
            currentPetType = try { PetType.valueOf(petTypeName) } catch (e: Exception) { selectedPetStore.load() }
            selectedPetStore.save(currentPetType)
            userManuallyHidden = false
            if (isViewAttached) removePetOverlay()
        } else {
            currentPetType = selectedPetStore.load()
        }

        if (action == ACTION_DEBUG_TELA_WEB) {
            ensureOverlayReady()
            petView?.debugStartTelaWeb()
            return START_NOT_STICKY
        }
        if (action == ACTION_DEBUG_TELA_CORNER_WEB) {
            ensureOverlayReady()
            petView?.debugStartTelaCornerWeb()
            return START_NOT_STICKY
        }

        if (!selectedPetStore.isPetEnabled()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureOverlayReady()
        PetCareNotificationScheduler.schedule(this)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createPetOverlay() {
        if (isViewAttached) return
        windowManager = getSystemService(WindowManager::class.java)
        if (windowManager == null) {
            Log.e(TAG, "WindowManager unavailable")
            selectedPetStore.setPetEnabled(false)
            stopSelf()
            return
        }
        if (!canDrawOverlays()) {
            Log.e(TAG, "Overlay permission missing")
            selectedPetStore.setPetEnabled(false)
            stopSelf()
            return
        }

        val metrics = getDisplayMetrics()
        val petSize = (PET_SIZE_DP * resources.displayMetrics.density).toInt()
        // View 2x el pet: da espacio al aura (radio 0.85x) y a los floats (0.9x)
        // sin que se corten en el borde.
        val viewSize = (petSize * 2.0f).toInt()
        // Tope defensivo: si PET_SIZE_DP crece demasiado, el view (2x el sprite)
        // taparía la app de debajo. Nunca superamos el 40% del ancho de pantalla.
        val maxViewSize = (metrics.widthPixels * MAX_VIEW_SIZE_RATIO).toInt()
        val safeViewSize = minOf(viewSize, maxViewSize)

        telaWebOverlay = if (currentPetType == PetType.TELA) {
            TelaWebOverlayController(
                context = this,
                windowManager = windowManager!!,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
            )
        } else {
            null
        }

        petView = PetView(
            this,
            metrics.widthPixels,
            metrics.heightPixels,
            petSize,
            currentPetType,
            onTelaSilkChanged = ::onTelaSilkChanged,
            onTelaCornerWebChanged = ::onTelaCornerWebChanged,
        )
        if (BuildConfig.CARE_SCENES_ENABLED && currentPetType in DesktopCarePlayback.SUPPORTED_PETS &&
            CarePoseLoader.isAvailable(assets, currentPetType)) {
            if (currentPetType == PetType.CORGI) {
                fetchBall = CorgiFetchBallOverlay(this, windowManager!!, petSize)
                petView?.onFetchBallChanged = { frame ->
                    if (fetchBall?.render(frame) == false) petView?.cancelDesktopCare()
                }
            }
            careOverlay = CorgiCareCloud(this, windowManager!!, petSize, currentPetType,
                readStatus = { petView?.petStatus }) { action ->
                if (!isCareRoomVisible && shouldShowPetForPolicy()) petView?.startDesktopCare(action)
            }
            petView?.onCareStatusChanged = { careOverlay?.refreshActions() }
            petView?.onCareDismiss = { careOverlay?.close() }
            petView?.onDesktopPositionChanged = { x, y -> careOverlay?.follow(x, y) }
            petView?.onCareAffordance = {
                if (!isCareRoomVisible && shouldShowPetForPolicy()) {
                    val position: WindowManager.LayoutParams? = petView?.getWindowParams()
                    if (position != null) careOverlay?.show(position.x + petSize / 2, position.y)
                }
            }
        }

        // Evita robar foco al sistema (mejora back/gestos), manteniendo el overlay touchable.
        // adjustNothing: el sistema no debe panear/insetar la ventana cuando abre el teclado;
        // la mascota se reposiciona por nuestra cuenta según el IME.
        val params = WindowManager.LayoutParams(
            safeViewSize,
            safeViewSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseOverlayFlags,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            x = metrics.widthPixels / 2 - safeViewSize / 2
            y = metrics.heightPixels / 3
        }
        try {
            windowManager?.addView(petView, params)
            isViewAttached = true
            lastAppliedPetVisible = null
            applyPetOverlayVisible(shouldShowPetForPolicy())
            startHomeForegroundPolling()
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "Overlay attach failed", e)
            selectedPetStore.setPetEnabled(false)
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Overlay attach failed", e)
            selectedPetStore.setPetEnabled(false)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Overlay attach failed", e)
            selectedPetStore.setPetEnabled(false)
            stopSelf()
        }
    }

    private fun removePetOverlay() {
        val previousCare: CorgiCareCloud? = careOverlay
        careOverlay = null
        previousCare?.close()
        homeCheckHandler.removeCallbacks(homeCheckRunnable)
        petView?.let {
            it.pauseAnimation()
            if (isViewAttached) {
                try { windowManager?.removeViewImmediate(it) } catch (_: Exception) { Log.w(TAG, "Overlay detach failed") }
                isViewAttached = false
            }
        }
        petView = null
        fetchBall?.close()
        fetchBall = null
        telaWebOverlay?.destroy()
        telaWebOverlay = null
        lastAppliedPetVisible = null
    }

    private fun onTelaSilkChanged(state: TelaSilkState?) {
        if (currentPetType == PetType.TELA) telaWebOverlay?.render(state)
    }

    private fun onTelaCornerWebChanged(state: TelaCornerWebState?) {
        if (currentPetType == PetType.TELA) telaWebOverlay?.renderCornerWeb(state)
    }

    /**
     * Con acceso de uso: solo visible en el lanzador. Sin acceso: siempre visible salvo ocultar manual.
     * No se puede poner un TYPE_APPLICATION_OVERLAY detrás de otras apps; se oculta para no taparlas.
     */
    private fun shouldShowPetForPolicy(): Boolean {
        if (userManuallyHidden || !isScreenOn || isCareRoomVisible) return false
        if (!DesktopForegroundHelper.hasUsageAccess(this)) return true
        return DesktopForegroundHelper.isLauncherForeground(this)
    }

    private fun refreshPetVisibilityForForeground() {
        if (!isViewAttached || petView == null) return
        val visible: Boolean = shouldShowPetForPolicy()
        if (!visible) careOverlay?.close()
        applyPetOverlayVisible(visible)
    }

    private fun applyPetOverlayVisible(visible: Boolean) {
        val v = petView ?: return
        val effectiveVisible: Boolean = visible && !isCareRoomVisible && isScreenOn
        if (!effectiveVisible) careOverlay?.close()
        val already = lastAppliedPetVisible == effectiveVisible &&
            v.visibility == if (effectiveVisible) View.VISIBLE else View.GONE
        if (already) return
        lastAppliedPetVisible = effectiveVisible
        v.visibility = if (effectiveVisible) View.VISIBLE else View.GONE
        telaWebOverlay?.setVisible(effectiveVisible)
        updateOverlayTouchThrough(!effectiveVisible)
        if (effectiveVisible) v.resumeAnimation() else v.pauseAnimation()
    }

    private fun updateOverlayTouchThrough(passThrough: Boolean) {
        val v = petView ?: return
        val wm = windowManager ?: return
        val p = v.layoutParams as? WindowManager.LayoutParams ?: return
        val newFlags = if (passThrough) {
            baseOverlayFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseOverlayFlags
        }
        if (p.flags == newFlags) return
        p.flags = newFlags
        try {
            wm.updateViewLayout(v, p)
        } catch (e: Exception) {
            Log.w(TAG, "Overlay flag update failed")
        }
    }

    private fun startHomeForegroundPolling() {
        homeCheckHandler.removeCallbacks(homeCheckRunnable)
        homeCheckHandler.postDelayed(homeCheckRunnable, HOME_POLL_INTERVAL_MS)
    }

    private fun canDrawOverlays(): Boolean = android.provider.Settings.canDrawOverlays(this)

    private fun getDisplayMetrics(): DisplayMetrics {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.currentWindowMetrics?.bounds ?: Rect()
            DisplayMetrics().apply {
                widthPixels = bounds.width().coerceAtLeast(1)
                heightPixels = bounds.height().coerceAtLeast(1)
                density = resources.displayMetrics.density
                densityDpi = resources.displayMetrics.densityDpi
            }
        } else {
            DisplayMetrics().also {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getRealMetrics(it)
                if (it.widthPixels <= 0 || it.heightPixels <= 0) {
                    it.setTo(resources.displayMetrics)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        refreshNotificationChannel(this)
    }

    private fun buildNotification(isHidden: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, PetDashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, PetService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, PetService::class.java).setAction(
                if (isHidden) ACTION_SHOW else ACTION_HIDE
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_active_title))
            .setContentText(
                getString(if (isHidden) R.string.notif_hidden_subtitle else R.string.notif_active_subtitle)
            )
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                getString(if (isHidden) R.string.notif_show_pet else R.string.notif_hide_pet),
                toggleIntent,
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_stop_pet), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                val rawTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                val temperatureCelsius = rawTemperature
                    .takeUnless { it == Int.MIN_VALUE }
                    ?.div(10f)
                petView?.onBatteryChanged(percent, isCharging)
                petView?.onBatteryTemperatureChanged(temperatureCelsius)
            }
        }
        try {
            ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            Log.w(TAG, "Battery receiver registration failed")
            batteryReceiver = null
        }
    }
    
    private fun registerAirplaneReceiver() {
        if (airplaneReceiver != null) return
        airplaneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isAirplane = intent.getBooleanExtra("state", false)
                petView?.onAirplaneModeChanged(isAirplane)
            }
        }
        try {
            ContextCompat.registerReceiver(this, airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            Log.w(TAG, "Airplane receiver registration failed")
            airplaneReceiver = null
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = ScreenStateReceiver { on ->
            isScreenOn = on
            if (!on) careOverlay?.close()
            applyPetOverlayVisible(shouldShowPetForPolicy())
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        try {
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Exception) {
            Log.w(TAG, "Screen receiver registration failed")
            screenReceiver = null
        }
    }

    private fun ensureForeground() {
        if (!isForegroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification(userManuallyHidden))
            isForegroundStarted = true
        }
    }

    private fun updateNotification(isHidden: Boolean) {
        if (!isForegroundStarted) return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(isHidden),
        )
    }

    private fun ensureOverlayReady() {
        if (!isViewAttached) {
            createPetOverlay()
            if (!isViewAttached) return
            registerScreenReceiver()
            registerBatteryReceiver()
            registerAirplaneReceiver()
        } else {
            startHomeForegroundPolling()
            if (!userManuallyHidden) {
                applyPetOverlayVisible(shouldShowPetForPolicy())
            }
        }
    }

    override fun onDestroy() {
        careScope.cancel()
        removePetOverlay()
        isForegroundStarted = false
        try { 
            batteryReceiver?.let { unregisterReceiver(it) }
            airplaneReceiver?.let { unregisterReceiver(it) }
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {}
        batteryReceiver = null
        airplaneReceiver = null
        screenReceiver = null
        isRunning = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration): Unit {
        careOverlay?.close()
        petView?.cancelDesktopCare()
        super.onConfigurationChanged(newConfig)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        selectedPetStore.setPetEnabled(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}
