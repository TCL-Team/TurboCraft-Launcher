/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.chat

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.notification.NotificationChannelData
import com.movtery.zalithlauncher.notification.NotificationManager
import com.movtery.zalithlauncher.ui.activities.SplashActivity

internal const val EXTRA_OPEN_CHAT = "com.movtery.zalithlauncher.extra.OPEN_CHAT"

/** Posts a local Android alert for live chat events. Delivery still depends on an active session. */
internal object ChatNotificationHelper {
    fun showIncomingMessage(
        context: Context,
        conversationId: String,
        senderName: String,
        preview: String
    ) {
        if (!NotificationManager.checkNotificationEnabled(context)) return

        runCatching {
            val channel = NotificationChannelData.CHAT_MESSAGES_CHANNEL
            NotificationManager.createNotificationChannel(context, channel)
            val notificationId = conversationId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)
            val openChatIntent = Intent(context, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_CHAT, true)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openChatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channel.channelId)
                .setSmallIcon(R.drawable.ic_chat_bubble)
                .setContentTitle(senderName.ifBlank { "New chat message" })
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
