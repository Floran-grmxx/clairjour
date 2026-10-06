package com.clairjour.app.shield

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
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
    private var debugRemote: BroadcastReceiver? = null

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
        if (BuildConfig.DEBUG) registerDebugRemote()
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
        debugRemote?.let(::unregisterReceiver)
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
            if (BuildConfig.DEBUG && actions.isNotEmpty()) Log.d(TAG, "surface=${screen.surface} actions=$actions")
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
                    val homeTab = root?.let { findById(it, listOf(InstagramSelectors.HOME_TAB_ID)) }
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
        logDump(dump)
    }

    /**
     * Debug builds only: lets a developer drive Instagram from a computer while calibrating
     * the selectors, e.g. `adb shell am broadcast -a com.clairjour.shield.DEBUG -e command dump`.
     * Commands: dump, back, click_id <fragment>, click_label <label>, scroll <fragment>, tap <x y>,
     * type <text>, enter, rule_on <key>, rule_off <key>,
     * shield_on, shield_off.
     */
    private fun registerDebugRemote() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val command = intent.getStringExtra("command") ?: return
                val argument = intent.getStringExtra("argument").orEmpty()
                val root = rootInActiveWindow
                val succeeded = when (command) {
                    "dump" -> {
                        root?.let { logDump(AccessibilityTree.describe(AccessibilityTree.capture(it))) }
                        root != null
                    }
                    "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                    "click_id" -> root?.let { findById(it, listOf(argument)) }?.let(::clickWithAncestors) ?: false
                    "click_label" -> root?.let { findByLabel(it, setOf(argument.lowercase())) }
                        ?.let(::clickWithAncestors) ?: false
                    "scroll" -> root?.let { findById(it, listOf(argument)) }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                    "tap" -> {
                        val (x, y) = argument.split(' ').map(String::toFloat)
                        val path = Path().apply { moveTo(x, y) }
                        dispatchGesture(
                            GestureDescription.Builder()
                                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MILLIS))
                                .build(),
                            null,
                            null
                        )
                    }
                    "type" -> root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, argument)
                        }
                    ) ?: false
                    "enter" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                        ) == true
                    "rule_on", "rule_off" -> {
                        val rule = ShieldRule.entries.firstOrNull { it.key == argument }
                        val repository = (application as ClairjourApplication).container.settingsRepository
                        rule?.let { scope.launch { repository.setShieldRule(it, command == "rule_on") } }
                        rule != null
                    }
                    "shield_on", "shield_off" -> {
                        val repository = (application as ClairjourApplication).container.settingsRepository
                        scope.launch { repository.setShieldEnabled(command == "shield_on") }
                        true
                    }
                    else -> false
                }
                Log.d(TAG, "DEBUG command=$command argument=$argument succeeded=$succeeded")
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(DEBUG_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        debugRemote = receiver
    }

    /** Logcat truncates long entries: one entry per line keeps the dump readable. */
    private fun logDump(dump: String) {
        Log.d(TAG, "DUMP START")
        dump.lineSequence().filter { it.isNotBlank() }.forEach { Log.d(TAG, it) }
        Log.d(TAG, "DUMP END")
    }

    private companion object {
        const val DEBUG_ACTION = "com.clairjour.shield.DEBUG"
        const val TAP_DURATION_MILLIS = 50L
        const val TAG = "ClairjourShield"
        const val EVALUATION_DELAY_MILLIS = 120L
    }
}
