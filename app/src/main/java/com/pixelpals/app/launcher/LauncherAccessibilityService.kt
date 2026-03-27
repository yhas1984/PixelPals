package com.pixelpals.app.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class LauncherAccessibilityService : AccessibilityService() {
    private var lastSavedSignature = ""
    private var lastSaveAt = 0L

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()
            ?: rootInActiveWindow?.packageName?.toString()
            ?: return
        if (!isLauncherPackage(packageName)) return

        val root = rootInActiveWindow ?: return
        val candidates = mutableListOf<Rect>()
        collectCandidates(root, candidates)

        val deduped = candidates
            .distinctBy { rect -> "${rect.centerX() / 8},${rect.centerY() / 8}" }
            .sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
            .take(28)

        if (deduped.isEmpty()) return

        val signature = deduped.joinToString("|") { rect ->
            "${rect.left},${rect.top},${rect.right},${rect.bottom}"
        }
        val now = System.currentTimeMillis()
        if (signature != lastSavedSignature || now - lastSaveAt > 2_000L) {
            LauncherPlatformRepository.saveRects(this, deduped)
            lastSavedSignature = signature
            lastSaveAt = now
        }
    }

    override fun onInterrupt() = Unit

    private fun isLauncherPackage(packageName: String): Boolean {
        val defaultLauncher = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0
        )?.activityInfo?.packageName

        return packageName == defaultLauncher ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName.contains("home", ignoreCase = true)
    }

    private fun collectCandidates(node: AccessibilityNodeInfo, out: MutableList<Rect>) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val density = resources.displayMetrics.density
        val minSize = (40f * density).roundToInt()
        val maxSize = (180f * density).roundToInt()
        val width = rect.width()
        val height = rect.height()
        val squareish = width in minSize..maxSize &&
            height in minSize..(maxSize * 2) &&
            abs(width - height) <= max(width, height) * 0.75f

        val hasLabel = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val visible = node.isVisibleToUser && rect.width() > 0 && rect.height() > 0 && rect.top > 0
        val interactive = node.isClickable || node.isFocusable || node.isLongClickable

        if (visible && interactive && hasLabel && squareish) {
            out += Rect(rect)
        }

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                collectCandidates(child, out)
            }
        }
    }
}
