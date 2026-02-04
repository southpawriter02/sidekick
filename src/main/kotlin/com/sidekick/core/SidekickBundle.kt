// =============================================================================
// SidekickBundle.kt
// =============================================================================
// Localization bundle for the Sidekick plugin.
//
// This class provides type-safe access to localized strings stored in
// messages/SidekickBundle.properties. Using a bundle allows us to:
//
// 1. Centralize all user-facing strings in one place
// 2. Support future internationalization (i18n)
// 3. Use IntelliJ's @PropertyKey annotation for compile-time validation
//
// USAGE:
// ```kotlin
// val title = SidekickBundle.message("toolwindow.title")
// val greeting = SidekickBundle.message("chat.welcome", userName)
// ```
//
// The message() function supports parameter substitution using {0}, {1}, etc.
// in the properties file.
// =============================================================================

package com.sidekick.core

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

// -----------------------------------------------------------------------------
// Bundle Path Constant
// -----------------------------------------------------------------------------
// The path to the properties file, relative to the resources directory.
// This must match the actual file location exactly.
// @NonNls indicates this string should not be translated.
// -----------------------------------------------------------------------------
@NonNls
private const val BUNDLE = "messages.SidekickBundle"

/**
 * Localization bundle for Sidekick plugin strings.
 *
 * This object provides access to all user-facing strings in the plugin,
 * supporting localization and parameter substitution.
 *
 * ## Adding New Strings
 *
 * 1. Add the key-value pair to `resources/messages/SidekickBundle.properties`
 * 2. Access it via `SidekickBundle.message("your.key")`
 *
 * ## Parameter Substitution
 *
 * Properties file:
 * ```properties
 * welcome.message=Hello, {0}! You have {1} unread messages.
 * ```
 *
 * Kotlin usage:
 * ```kotlin
 * SidekickBundle.message("welcome.message", userName, messageCount)
 * ```
 *
 * @see com.intellij.DynamicBundle
 */
object SidekickBundle : DynamicBundle(BUNDLE) {

    /**
     * Retrieves a localized message by its key, with optional parameter substitution.
     *
     * @param key The message key from SidekickBundle.properties.
     *            The @PropertyKey annotation enables IDE validation and completion.
     * @param params Optional parameters to substitute into the message.
     *               These replace {0}, {1}, etc. in the properties file.
     * @return The localized, parameter-substituted message string.
     *
     * @throws MissingResourceException if the key doesn't exist in the bundle
     */
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String {
        return getMessage(key, *params)
    }
}
