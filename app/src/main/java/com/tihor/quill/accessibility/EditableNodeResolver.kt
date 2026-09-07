package com.tihor.quill.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Resolves the *true* editable node from the focused node (spec §5A). The node holding focus is
 * often a semantic wrapper, not the editable field, so we look: the focused node itself → its
 * descendants → its nearest editable ancestor. A node qualifies only if it is editable **and**
 * exposes `ACTION_SET_TEXT` (WebView/Chrome inputs frequently don't — those resolve to null and the
 * caller drops to the clipboard fallback).
 */
object EditableNodeResolver {

    /** Compose may report a semantic container from findFocus; inspect its focused descendant. */
    fun findInputTarget(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus?.isEditable == true) return focus
        return findFocusedEditable(focus) ?: findFocusedEditable(root)
    }

    fun supportsSetText(node: AccessibilityNodeInfo): Boolean =
        node.actionList.any {
            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
        }

    private fun isTarget(node: AccessibilityNodeInfo): Boolean =
        node.isEditable && supportsSetText(node)

    /**
     * The editable node that actually holds **input focus** within [root]'s subtree (regardless of
     * whether it supports SET_TEXT). Used for the sensitivity guard so we judge the field the user
     * is really on — not a SET_TEXT-capable sibling the [resolve] target search might wander to.
     */
    fun findFocusedEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        if (root.isEditable && root.isFocused) return root
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_DESCENDANTS_SCANNED) {
            val node = queue.removeFirst()
            visited++
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (child.isEditable && child.isFocused) return child
                queue.add(child)
            }
        }
        return null
    }

    fun resolve(focus: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        focus ?: return null
        // 1. The focused node itself.
        if (isTarget(focus)) return focus
        // 2. A descendant we can *confidently tie to focus* (isFocused). We deliberately do NOT pick
        //    "the first editable descendant" — in a multi-input container (e.g. a login form behind a
        //    WebView FrameLayout) that would wander to the wrong field, even into/next to a password.
        //    When the focused editable can't be identified, the caller drops to the clipboard rung.
        findFocusedTargetDescendant(focus)?.let { return it }
        // 3. Nearest editable ancestor of the focused node (safe — it contains the focus).
        var ancestor = focus.parent
        var guard = 0
        while (ancestor != null && guard < MAX_ANCESTOR_HOPS) {
            if (isTarget(ancestor)) return ancestor
            ancestor = ancestor.parent
            guard++
        }
        return null
    }

    /** BFS for an editable, SET_TEXT-capable descendant that currently holds input focus. */
    private fun findFocusedTargetDescendant(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_DESCENDANTS_SCANNED) {
            val node = queue.removeFirst()
            visited++
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (isTarget(child) && child.isFocused) return child
                queue.add(child)
            }
        }
        return null
    }

    private const val MAX_ANCESTOR_HOPS = 12
    private const val MAX_DESCENDANTS_SCANNED = 400
}
