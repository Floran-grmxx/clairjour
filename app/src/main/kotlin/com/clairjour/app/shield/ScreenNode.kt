package com.clairjour.app.shield

/** Screen rectangle in absolute screen pixels. */
data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/**
 * Lightweight, immutable copy of an accessibility node. The service captures the live tree
 * into this form so that all detection logic stays pure and unit-testable.
 *
 * [viewId] is the short resource name, without the "com.instagram.android:id/" prefix.
 */
data class ScreenNode(
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val isSelected: Boolean = false,
    val isClickable: Boolean = false,
    val bounds: NodeBounds = NodeBounds(0, 0, 0, 0),
    val children: List<ScreenNode> = emptyList()
) {
    /** Visible text, falling back to the content description. */
    val label: String?
        get() = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() }

    /** Depth-first traversal of this node and all its descendants. */
    fun walk(): Sequence<ScreenNode> = sequence {
        yield(this@ScreenNode)
        children.forEach { yieldAll(it.walk()) }
    }

    /**
     * Depth-first traversal that also reports whether the node or one of its ancestors is
     * selected (tabs often put the selected flag on the container, not on the label).
     */
    fun walkWithSelection(ancestorSelected: Boolean = false): Sequence<Pair<ScreenNode, Boolean>> =
        sequence {
            val selected = ancestorSelected || isSelected
            yield(this@ScreenNode to selected)
            children.forEach { yieldAll(it.walkWithSelection(selected)) }
        }
}
