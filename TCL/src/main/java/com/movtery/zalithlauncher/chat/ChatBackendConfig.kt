/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

/** Supabase publishable key is intended to ship in the app; database access is restricted by RLS. */
internal object ChatBackendConfig {
    const val PROJECT_URL = "https://qvjanukzamquosjxhxnc.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_ErkdgkvWDiVqY6_xE0mZAw_yj-nptNo"
    const val CHAT_ACCOUNT_SESSION_FUNCTION = "$PROJECT_URL/functions/v1/chat-account-session"
    const val CHAT_ACCOUNT_RECOVERY_FUNCTION = "$PROJECT_URL/functions/v1/chat-account-recovery"
}
