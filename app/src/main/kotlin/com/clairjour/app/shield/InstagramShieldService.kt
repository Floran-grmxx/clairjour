package com.clairjour.app.shield

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clairjour.app.BuildConfig
import com.clairjour.app.ClairjourApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches Instagram only: every event coming from another app is ignored, except to notice
 * that Instagram left the foreground. Nothing is stored or sent anywhere.
 */
class InstagramShieldService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val engine = ShieldEngine(SystemClock::elapsedRealtime)
    private lateinit var cover: CoverOverlay

    @Volatile private var settings = ShieldSettings()
    private var evaluationPending = false
    private var lastDiagnosticDump: String? = null

    private val evaluation = Runnable {
        evaluationPending = false
        evaluateScreen()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        cover = CoverOverlay(this)
        val repository = (application as ClairjourApplication).container.settingsRepository
        scope.launch {
            repository.shieldSettingsFlow.collect {
                settings = it
                scheduleEvaluation()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == InstagramSelectors.PACKAGE_NAME) {
            scheduleEvaluation()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Keyboards and system pop-ups also send this event: only the active window counts.
            scheduleEvaluation()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(evaluation)
        if (::cover.isInitialized) cover.hide()
        scope.cancel()
        super.onDestroy()
    }

    private fun scheduleEvaluation() {
        if (evaluationPending) return
        evaluationPending = true
        handler.postDelayed(evaluation, EVALUATION_DELAY_MILLIS)
    }

    private fun evaluateScreen() {
        val root = rootInActiveWindow
        if (root == null || root.packageName != InstagramSelectors.PACKAGE_NAME) {
            apply(engine.reset(), root)
            root?.let(AccessibilityTree::recycle)
            return
        }
        try {
            val tree = AccessibilityTree.capture(root)
            val screen = InstagramScreenClassifier.classify(tree)
            val actions = engine.onScreen(screen, settings.activeRules)
            logDiagnostic(tree, screen, actions)
            apply(actions, root)
        } catch (error: RuntimeException) {
            // The tree can change while it is read: the next event triggers a new evaluation.
            Log.w(TAG, "Screen evaluation failed", error)
        } finally {
            AccessibilityTree.recycle(root)
        }
    }

    private fun apply(actions: List<ShieldAction>, root: AccessibilityNodeInfo?) {
        for (action in actions) {
            when (action) {
                is ShieldAction.GoBack -> performGlobalAction(GLOBAL_ACTION_BACK)
                ShieldAction.OpenHomeTab -> {
                    val homeTab = root?.let { findById(it, InstagramSelectors.homeTabIds) }
                    if (homeTab == null || !clickWithAncestors(homeTab)) performGlobalAction(GLOBAL_ACTION_BACK)
                }
                ShieldAction.SelectAccountsTab -> {
                    root?.let { findByLabel(it, InstagramSelectors.searchAccountsTabLabels) }
                        ?.let(::clickWithAncestors)
                }
                is ShieldAction.ShowCover -> runCatching { cover.show(action.bounds) }
                    .onFailure { Log.w(TAG, "Cover display failed", it) }
                ShieldAction.HideCover -> cover.hide()
            }
        }
    }

    private fun findById(root: AccessibilityNodeInfo, fragments: List<String>): AccessibilityNodeInfo? =
        fragments.firstNotNullOfOrNull { fragment ->
            root.findAccessibilityNodeInfosByViewId(InstagramSelectors.VIEW_ID_PREFIX + fragment)
                .firstOrNull { it.isVisibleToUser }
        }

    private fun findByLabel(node: AccessibilityNodeInfo, labels: Set<String>): AccessibilityNodeInfo? {
        val label = (node.text ?: node.contentDescription)?.toString()?.lowercase()?.trim()
        if (label in labels && node.isVisibleToUser) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findByLabel(child, labels)?.let { return it }
        }
        return null
    }

    private fun clickWithAncestors(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
        }
        return false
    }

    private fun logDiagnostic(tree: ScreenNode, screen: InstagramScreen, actions: List<ShieldAction>) {
        if (!BuildConfig.DEBUG || !settings.diagnostic) return
        val dump = AccessibilityTree.describe(tree)
        if (dump == lastDiagnosticDump && actions.isEmpty()) return
        lastDiagnosticDump = dump
        Log.d(TAG, "screen=$screen surface=${screen.surface} actions=$actions")
        dump.chunked(LOG_CHUNK_LENGTH).forEach { Log.d(TAG, it) }
    }

    private companion object {
        const val TAG = "ClairjourShield"
        const val EVALUATION_DELAY_MILLIS = 120L
        const val LOG_CHUNK_LENGTH = 3_500
    }
}
