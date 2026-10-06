package com.clairjour.app.shield

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Converts the live accessibility tree into [ScreenNode] copies. */
object AccessibilityTree {

    private const val MAX_DEPTH = 60
    private const val MAX_NODES = 3_000

    fun capture(root: AccessibilityNodeInfo): ScreenNode {
        val budget = intArrayOf(MAX_NODES)
        return copy(root, depth = 0, budget = budget) ?: ScreenNode()
    }

    private fun copy(node: AccessibilityNodeInfo, depth: Int, budget: IntArray): ScreenNode? {
        if (!node.isVisibleToUser || budget[0] <= 0) return null
        budget[0]--

        val children = mutableListOf<ScreenNode>()
        if (depth < MAX_DEPTH) {
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                copy(child, depth + 1, budget)?.let(children::add)
                recycle(child)
            }
        }

        val rect = Rect().also(node::getBoundsInScreen)
        return ScreenNode(
            viewId = node.viewIdResourceName?.removePrefix(InstagramSelectors.VIEW_ID_PREFIX),
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            isSelected = node.isSelected,
            isClickable = node.isClickable,
            bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
            children = children
        )
    }

    @Suppress("DEPRECATION")
    fun recycle(node: AccessibilityNodeInfo) {
        // Recycling is a no-op from Android 13; older versions still pool nodes.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) node.recycle()
    }

    /**
     * Readable dump used by the diagnostic mode to calibrate [InstagramSelectors].
     * Labels are truncated so that conversations never appear in full in the logs.
     */
    fun describe(node: ScreenNode, depth: Int = 0, output: StringBuilder = StringBuilder()): String {
        val label = node.label?.replace('\n', ' ')?.take(DIAGNOSTIC_LABEL_LENGTH)
        output.append("  ".repeat(depth))
            .append(node.viewId ?: "-")
            .append(" [").append(node.className?.substringAfterLast('.') ?: "?").append(']')
        if (label != null) output.append(" \"").append(label).append('"')
        if (node.isSelected) output.append(" SELECTED")
        if (node.isClickable) output.append(" clickable")
        with(node.bounds) { output.append(" ($left,$top-$right,$bottom)") }
        output.append('\n')
        node.children.forEach { describe(it, depth + 1, output) }
        return output.toString()
    }

    private const val DIAGNOSTIC_LABEL_LENGTH = 24
}
