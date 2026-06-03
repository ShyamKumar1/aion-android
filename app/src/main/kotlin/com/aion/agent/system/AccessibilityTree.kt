package com.aion.agent.system

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts an [AccessibilityNodeInfo] tree into a token-efficient string
 * the LLM can understand. Per AION_GUIDELINES §10:
 *  - Skip invisible elements
 *  - Truncate long texts (>100 chars)
 *  - Deduplicate identical adjacent entries
 *  - Mark clickable elements as [tapable]
 */
@Singleton
class AccessibilityTree @Inject constructor() {

    data class UiNode(
        val id: Int,
        val text: String?,
        val className: String?,
        val clickable: Boolean,
        val bounds: Rect?,
        val children: List<UiNode> = emptyList(),
    )

    /**
     * Parse an [AccessibilityNodeInfo] root into our [UiNode] tree.
     */
    fun parse(root: AccessibilityNodeInfo): UiNode = mapNode(root, 0)

    /**
     * Produce a token-efficient string representation for LLM consumption.
     * This is the primary method used by the agent loop.
     */
    fun toTokenEfficientString(root: AccessibilityNodeInfo): String {
        val uiTree = parse(root)
        return buildString {
            appendLine("--- Screen Content ---")
            flatten(uiTree, 0, mutableSetOf())
        }
    }

    /**
     * Flatten the tree into indented text lines, deduplicating as we go.
     */
    private var passwordFieldNoteAdded = false

    private fun StringBuilder.flatten(node: UiNode, depth: Int, seen: MutableSet<String>) {
        val rawText = node.text?.take(100) ?: return // skip nodes without text

        // Skip password field placeholder nodes; add note once
        if (rawText == "[password field]") {
            if (!passwordFieldNoteAdded) {
                passwordFieldNoteAdded = true
                appendLine("[A password field is present on screen]")
            }
            return
        }

        val sig = "${node.className}|${rawText}"
        if (sig in seen) return
        seen.add(sig)

        val indent = "  ".repeat(depth.coerceAtMost(8))
        val clickable = if (node.clickable) " [tapable]" else ""
        appendLine("$indent$rawText$clickable")

        for (child in node.children) {
            flatten(child, depth + 1, seen)
        }
    }

    /**
     * Recursively map [AccessibilityNodeInfo] to [UiNode].
     */
    private fun mapNode(info: AccessibilityNodeInfo, nextId: Int): UiNode {
        val bounds = Rect().also { info.getBoundsInScreen(it) }
        val children = mutableListOf<UiNode>()
        var childId = nextId + 1
        for (i in 0 until info.childCount) {
            info.getChild(i)?.let { child ->
                if (child.isVisibleToUser) {
                    children.add(mapNode(child, childId))
                    childId += countDescendants(child) + 1
                }
                child.recycle()
            }
        }
        // Redact password field content per AION_GUIDELINES §10
        val isPassword = info.isPassword
        val displayText = if (isPassword) "[password field]" else info.text?.toString()
        return UiNode(
            id = nextId,
            text = displayText,
            className = info.className?.toString(),
            clickable = info.isClickable,
            bounds = if (info.isVisibleToUser) bounds else null,
            children = children,
        )
    }

    private fun countDescendants(node: AccessibilityNodeInfo): Int {
        var count = 0
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                count += 1 + countDescendants(child)
                child.recycle()
            }
        }
        return count
    }

    /** Whether a package is in the secure exclusion list. */
    fun isSecurePackage(packageName: String): Boolean =
        SECURE_PACKAGES.any { packageName.startsWith(it) }

    companion object {
        val SECURE_PACKAGES = listOf(
            "com.android.settings",
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.wallet",
            "com.lastpass.lpandroid",
            "com.agilebits.onepassword",
            "com.bitwarden.mobile",
            "com.oneplus.brickmode",
            // Banking apps (common prefixes)
            "com.chase",
            "com.wellsfargo",
            "com.bankofamerica",
            "com.usbank",
            "com.citi",
        )
    }
}
