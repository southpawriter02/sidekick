// =============================================================================
// SidekickIcons.kt
// =============================================================================
// Icon registry for the Sidekick plugin.
//
// This object provides centralized access to all icons used throughout the
// plugin. Icons are loaded using IntelliJ's IconLoader, which:
//
// 1. Handles HiDPI/Retina scaling automatically
// 2. Supports dark theme icon variants (icon_dark.svg)
// 3. Caches icons for performance
//
// ICON GUIDELINES:
// - Use SVG format for all icons (scalable, theme-aware)
// - Place icons in resources/icons/ directory
// - Use 16x16 as the base size for toolbar/menu icons
// - Use 13x13 for gutter icons, 12x12 for tree icons
// - Provide _dark.svg variants for icons that need different colors in dark themes
//
// NAMING CONVENTION:
// - Constant name: UPPERCASE_WITH_UNDERSCORES
// - File name: lowercase_with_underscores.svg
// =============================================================================

package com.sidekick.core

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Central registry of all icons used in the Sidekick plugin.
 *
 * ## Usage
 *
 * ```kotlin
 * // In a tool window factory
 * toolWindow.setIcon(SidekickIcons.SIDEKICK)
 *
 * // In an action
 * val action = object : AnAction("Chat", "Open chat", SidekickIcons.CHAT) { ... }
 * ```
 *
 * ## Adding New Icons
 *
 * 1. Add the SVG file to `resources/icons/`
 * 2. Add a constant here using IconLoader.getIcon()
 * 3. Use the constant throughout the codebase
 *
 * @see com.intellij.openapi.util.IconLoader
 */
object SidekickIcons {

    // -------------------------------------------------------------------------
    // Main Plugin Icon
    // -------------------------------------------------------------------------

    /**
     * The primary Sidekick plugin icon.
     *
     * Used in:
     * - Tool window tab
     * - Settings page
     * - Plugin marketplace listing
     *
     * Size: 16x16 (base), with automatic HiDPI scaling
     */
    @JvmField
    val SIDEKICK: Icon = IconLoader.getIcon("/icons/sidekick.svg", SidekickIcons::class.java)

    // -------------------------------------------------------------------------
    // Chat UI Icons
    // -------------------------------------------------------------------------

    /**
     * Chat bubble icon for the chat tool window.
     *
     * Used in:
     * - Tool window title
     * - Chat-related actions
     *
     * Size: 16x16
     */
    @JvmField
    val CHAT: Icon = IconLoader.getIcon("/icons/chat.svg", SidekickIcons::class.java)

    /**
     * Send message icon for the chat input area.
     *
     * Used in:
     * - Send button in chat panel
     * - "Send message" action
     *
     * Size: 16x16
     */
    @JvmField
    val SEND: Icon = IconLoader.getIcon("/icons/send.svg", SidekickIcons::class.java)

    // -------------------------------------------------------------------------
    // Status Icons (for future use in connection status widget)
    // -------------------------------------------------------------------------
    // NOTE: These will be added in v0.1.3 when we implement the status indicator.
    // For now, the connection status uses colored dots drawn programmatically.
    // -------------------------------------------------------------------------
}
