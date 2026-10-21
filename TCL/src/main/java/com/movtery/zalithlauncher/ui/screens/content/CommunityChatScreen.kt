/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.ui.screens.content

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.chat.ChatController
import com.movtery.zalithlauncher.chat.ChatFriend
import com.movtery.zalithlauncher.chat.ChatPresence
import com.movtery.zalithlauncher.chat.ChatFriendRequest
import com.movtery.zalithlauncher.chat.ChatMessage
import com.movtery.zalithlauncher.chat.ChatNotification
import com.movtery.zalithlauncher.chat.ChatPanel
import com.movtery.zalithlauncher.chat.ChatProfile
import com.movtery.zalithlauncher.chat.ChatUiState
import com.movtery.zalithlauncher.chat.chatPresence
import com.movtery.zalithlauncher.chat.presence
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftAccount
import com.movtery.zalithlauncher.notification.NotificationManager
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

@Composable
fun CommunityChatScreen(backStackViewModel: ScreenBackStackViewModel) {
    val context = LocalContext.current
    val launcherAccount by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val controllerKey = launcherAccount?.let {
        "${it.uniqueUUID}:${it.accountType}:${it.username}:${it.profileId}:${it.accessToken.hashCode()}"
    }
    val controller = remember(controllerKey) { ChatController(context.applicationContext, launcherAccount) }
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var restoringChat by rememberSaveable(controllerKey) { mutableStateOf(false) }

    LaunchedEffect(controller) { controller.start() }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    BaseScreen(
        screenKey = NormalNavKey.Chat,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        LaunchedEffect(controller, isVisible, lifecycleOwner) {
            if (!isVisible) {
                controller.setScreenVisible(false)
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.setScreenVisible(true)
                try {
                    awaitCancellation()
                } finally {
                    controller.setScreenVisible(false)
                }
            }
        }
        val account = launcherAccount
        when {
            account == null -> ChatAccountRequired {
                backStackViewModel.mainScreen.navigateTo(NormalNavKey.AccountManager())
            }
            state.busy && state.profile == null && !state.needsProfile -> ChatLoadingPanel(account.username)
            restoringChat -> ChatRecoveryPanel(state, controller, onCancel = { restoringChat = false })
            state.needsProfile -> ChatProfileSetup(state, controller, account, onRecover = { restoringChat = true })
            state.session == null || (state.error != null && state.profile == null) ->
                ChatConnectionPanel(
                    state = state,
                    controller = controller,
                    backStackViewModel = backStackViewModel,
                    onRecover = { restoringChat = true }
                )
            else -> ChatWorkspace(state, controller, account)
        }
    }
}

@Composable
private fun ChatAccountRequired(onOpenAccounts: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Your launcher account is your chat identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Select a Microsoft or offline account first. Chat will use that account automatically; you only need to choose a chat username.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenAccounts) { Text("Open Account Manager") }
            }
        }
    }
}

@Composable
private fun ChatLoadingPanel(accountName: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Connecting chat for $accountName…", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChatConnectionPanel(
    state: ChatUiState,
    controller: ChatController,
    backStackViewModel: ScreenBackStackViewModel,
    onRecover: () -> Unit
) {
    val errorText = state.error.orEmpty()
    val needsMicrosoftRefresh = errorText.contains("Account Manager", ignoreCase = true) ||
        errorText.contains("Microsoft", ignoreCase = true)
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(modifier = Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chat couldn't connect", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    state.error ?: "Check your connection, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Chat stays linked to this same launcher account. You don't need to create another account to retry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = controller::retry, enabled = !state.busy) { Text("Try again") }
                    if (needsMicrosoftRefresh) {
                        OutlinedButton(onClick = {
                            backStackViewModel.mainScreen.navigateTo(NormalNavKey.AccountManager())
                        }) { Text("Account Manager") }
                    }
                }
                TextButton(onClick = onRecover, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Have a recovery code? Restore existing Chat ID")
                }
            }
        }
    }
}

@Composable
private fun ChatProfileSetup(
    state: ChatUiState,
    controller: ChatController,
    account: Account,
    onRecover: () -> Unit
) {
    var username by rememberSaveable(account.uniqueUUID, state.suggestedUsername) {
        mutableStateOf(state.suggestedUsername)
    }
    Box(
        modifier = Modifier.fillMaxSize().imePadding().padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Set up your community profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FriendAvatar(account.username)
                            Column {
                                Text(account.username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${account.chatAccountLabel()} identity",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        "Choose the username your friends will search for. Your launcher login stays unchanged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Chat username") },
                        placeholder = { Text("e.g. craft_player") },
                        singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!state.busy && username.isNotBlank()) controller.completeProfile(username)
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "3–20 characters: letters, numbers, dots or underscores.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.error?.let { FeedbackBanner(it, isError = true) }
                }
                Button(
                    onClick = { controller.completeProfile(username) },
                    enabled = !state.busy && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Create profile and continue")
                }
                TextButton(onClick = onRecover, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Already have a chat ID? Restore it")
                }
            }
        }
    }
}

@Composable
private fun ChatRecoveryPanel(
    state: ChatUiState,
    controller: ChatController,
    onCancel: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var recoveryCode by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Restore your chat ID", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Enter the chat username and recovery code you saved earlier. This restores your existing profile, friends, requests, and messages; it does not change your Minecraft launcher login.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Chat username") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recoveryCode,
                    onValueChange = { value ->
                        recoveryCode = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
                    },
                    label = { Text("Recovery code") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (!state.busy) controller.recoverProfile(username, recoveryCode)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { FeedbackBanner(it, isError = true) }
                Button(
                    onClick = { controller.recoverProfile(username, recoveryCode) },
                    enabled = !state.busy && username.isNotBlank() && recoveryCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Restore my chat profile")
                }
                TextButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to profile setup")
                }
            }
        }
    }
}

@Composable
private fun ChatWorkspace(state: ChatUiState, controller: ChatController, launcherAccount: Account) {
    var draft by remember(state.selectedFriendId) { mutableStateOf("") }
    var friendUsername by rememberSaveable { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    val unreadSenderIds = remember(state.unreadNotifications) {
        state.unreadNotifications.mapTo(mutableSetOf<String>()) { it.senderId }
    }
    val selectedFriend = state.friends.firstOrNull { it.userId == state.selectedFriendId }

    Row(
        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.width(250.dp).fillMaxHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("FRIENDS & MESSAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Community", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            FriendAvatar(launcherAccount.username, presence = ChatPresence.ONLINE)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(launcherAccount.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("@${state.profile?.username.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(
                                launcherAccount.chatAccountLabel().take(3).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showProfileEditor = true }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_edit_outlined),
                                    contentDescription = "Edit chat profile",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    PanelButton("DMs", state.panel == ChatPanel.CHATS, Modifier.width(54.dp)) { controller.selectPanel(ChatPanel.CHATS) }
                    PanelButton("Requests${state.incomingRequests.size.takeIf { it > 0 }?.let { " $it" }.orEmpty()}", state.panel == ChatPanel.REQUESTS, Modifier.width(76.dp)) { controller.selectPanel(ChatPanel.REQUESTS) }
                    PanelButton("Alerts${state.unreadNotifications.size.takeIf { it > 0 }?.let { " $it" }.orEmpty()}", state.panel == ChatPanel.NOTIFICATIONS, Modifier.width(66.dp)) { controller.selectPanel(ChatPanel.NOTIFICATIONS) }
                    PanelButton("Add", state.panel == ChatPanel.ADD_FRIEND, Modifier.width(52.dp)) { controller.selectPanel(ChatPanel.ADD_FRIEND) }
                }
                HorizontalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.panel) {
                        ChatPanel.CHATS -> FriendList(
                            friends = state.friends,
                            selectedFriendId = state.selectedFriendId,
                            unreadSenderIds = unreadSenderIds,
                            onSelect = controller::selectFriend
                        )
                        ChatPanel.REQUESTS -> FriendRequests(
                            requests = state.incomingRequests,
                            busy = state.busy,
                            onRespond = controller::respondToRequest
                        )
                        ChatPanel.NOTIFICATIONS -> ChatNotificationsPanel(
                            notifications = state.unreadNotifications,
                            onSelect = controller::openNotification
                        )
                        ChatPanel.ADD_FRIEND -> AddFriendSidebarHint()
                    }
                }
                if (state.panel != ChatPanel.ADD_FRIEND) {
                    state.error?.let { FeedbackBanner(it, isError = true, modifier = Modifier.padding(8.dp)) }
                    state.notice?.let { FeedbackBanner(it, isError = false, modifier = Modifier.padding(8.dp)) }
                    TextButton(onClick = controller::refresh, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Refresh")
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            if (state.panel == ChatPanel.ADD_FRIEND) {
                AddFriendPanel(
                    username = friendUsername,
                    onUsernameChange = { friendUsername = it },
                    busy = state.busy,
                    notice = state.notice,
                    error = state.error,
                    onSend = { controller.addFriend(friendUsername) }
                )
            } else if (selectedFriend == null) {
                EmptyChatPanel(
                    text = if (state.friends.isEmpty()) "Your chat lobby is quiet. Add a friend to start a conversation." else "Choose a friend to open your conversation.",
                    onAddFriend = { controller.selectPanel(ChatPanel.ADD_FRIEND) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        FriendAvatar(
                            selectedFriend.displayName,
                            presence = selectedFriend.presence(),
                            hasUnread = selectedFriend.userId in unreadSenderIds
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedFriend.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("@${selectedFriend.username}  ·  Direct message", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PresenceLabel(selectedFriend.presence())
                    }
                    HorizontalDivider()
                    val listState = rememberLazyListState()
                    LaunchedEffect(selectedFriend.userId, state.messages.size) {
                        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.messages.isEmpty()) {
                            item("empty-chat") {
                                Text(
                                    "Say hello to ${selectedFriend.displayName} 👋",
                                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(state.messages, key = ChatMessage::id) { message ->
                            val ownMessage = message.senderId == state.session?.userId
                            MessageBubble(
                                message = message,
                                ownMessage = ownMessage,
                                busy = state.mutatingMessageId == message.id,
                                onEdit = { editingMessage = message },
                                onDelete = { deletingMessage = message }
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().imePadding().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { if (it.length <= 2000) draft = it },
                            placeholder = { Text("Message @${selectedFriend.username}") },
                            enabled = !state.sendingMessage,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                controller.sendMessage(draft)
                                draft = ""
                            }),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                controller.sendMessage(draft)
                                draft = ""
                            },
                            enabled = draft.isNotBlank() && !state.sendingMessage
                        ) {
                            if (state.sendingMessage) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Send")
                        }
                    }
                    state.error?.let { FeedbackBanner(it, isError = true, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                }
            }
        }
    }

    editingMessage?.let { message ->
        EditMessageDialog(
            message = message,
            busy = state.mutatingMessageId == message.id,
            onDismiss = { editingMessage = null },
            onSave = { text ->
                controller.editMessage(message, text)
                editingMessage = null
            }
        )
    }
    deletingMessage?.let { message ->
        DeleteMessageDialog(
            busy = state.mutatingMessageId == message.id,
            onDismiss = { deletingMessage = null },
            onConfirm = {
                controller.deleteMessage(message)
                deletingMessage = null
            }
        )
    }
    val ownProfile = state.profile
    if (showProfileEditor && ownProfile != null && state.recoveryCode == null) {
        ChatProfileEditorDialog(
            profile = ownProfile,
            busy = state.busy,
            error = state.error,
            notice = state.notice,
            onDismiss = { showProfileEditor = false; controller.clearFeedback() },
            onSave = controller::updateProfile,
            onCreateRecoveryCode = controller::createRecoveryCode
        )
    }
    state.recoveryCode?.let { code ->
        RecoveryCodeDialog(code = code, onSaved = controller::dismissRecoveryCode)
    }
}

@Composable
private fun ChatProfileEditorDialog(
    profile: ChatProfile,
    busy: Boolean,
    error: String?,
    notice: String?,
    onDismiss: () -> Unit,
    onSave: (username: String, displayName: String, bio: String) -> Unit,
    onCreateRecoveryCode: () -> Unit
) {
    var username by rememberSaveable(profile.id, profile.username) { mutableStateOf(profile.username) }
    var displayName by rememberSaveable(profile.id, profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable(profile.id, profile.bio) { mutableStateOf(profile.bio) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Edit chat profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Chat username") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(32) },
                    label = { Text("Display name") },
                    supportingText = { Text("1–32 characters") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(160) },
                    label = { Text("About me") },
                    supportingText = { Text("Up to 160 characters") },
                    maxLines = 3,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Your launcher account and Minecraft login are not changed by editing this profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onCreateRecoveryCode, enabled = !busy) {
                    Text("Create or replace recovery code")
                }
                Text(
                    "A new recovery code replaces the previous one. Keep it private and save it before reinstalling.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let { FeedbackBanner(it, isError = true) }
                notice?.let { FeedbackBanner(it, isError = false) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(username, displayName, bio) }, enabled = !busy) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") }
        }
    )
}

@Composable
private fun RecoveryCodeDialog(code: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Save your chat recovery code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Use this code with your chat username to restore the same chat profile after reinstall. Anyone with this code can access that chat profile, so keep it private.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Chat recovery code", code))
                }) {
                    Text("Copy recovery code")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaved) { Text("I've saved it") }
        }
    )
}

private fun Account.chatAccountLabel(): String = when {
    isMicrosoftAccount() -> "Microsoft"
    isLocalAccount() -> "Offline"
    else -> "Launcher"
}

@Composable
private fun PanelButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ChatNotificationsPanel(
    notifications: List<ChatNotification>,
    onSelect: (ChatNotification) -> Unit
) {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(NotificationManager.checkNotificationEnabled(context)) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted || NotificationManager.checkNotificationEnabled(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!notificationsAllowed) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Allow system alerts for new messages while chat is connected.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            NotificationManager.openNotificationSettings(context)
                        }
                    }) { Text("Enable") }
                }
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("You're all caught up. New message alerts will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                items(notifications, key = ChatNotification::id) { notification ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(MaterialTheme.shapes.medium).clickable { onSelect(notification) },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            FriendAvatar(
                                notification.senderDisplayName,
                                presence = chatPresence(notification.senderLastActiveAtMillis),
                                hasUnread = true
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(notification.senderDisplayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("@${notification.senderUsername} · ${notification.createdAt.substringAfter('T').take(5)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(notification.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendList(
    friends: List<ChatFriend>,
    selectedFriendId: String?,
    unreadSenderIds: Set<String>,
    onSelect: (ChatFriend) -> Unit
) {
    if (friends.isEmpty()) {
        EmptySidePanel("No friends yet. Use Add to find someone by username.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
        items(friends, key = ChatFriend::userId) { friend ->
            val selected = friend.userId == selectedFriendId
            val presence = friend.presence()
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(MaterialTheme.shapes.medium)
                    .clickable { onSelect(friend) },
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FriendAvatar(friend.displayName, presence = presence, hasUnread = friend.userId in unreadSenderIds)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(friend.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "@${friend.username} · ${presence.label()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = presenceColor(presence),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRequests(
    requests: List<ChatFriendRequest>,
    busy: Boolean,
    onRespond: (ChatFriendRequest, Boolean) -> Unit
) {
    if (requests.isEmpty()) {
        EmptySidePanel("No incoming friend requests.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
        items(requests, key = ChatFriendRequest::id) { request ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(request.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("@${request.username} wants to add you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRespond(request, true) }, enabled = !busy) { Text("Accept") }
                    OutlinedButton(onClick = { onRespond(request, false) }, enabled = !busy) { Text("Decline") }
                }
            }
        }
    }
}

@Composable
private fun AddFriendSidebarHint() {
    Box(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Search for your friend in the main panel →",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddFriendPanel(
    username: String,
    onUsernameChange: (String) -> Unit,
    busy: Boolean,
    notice: String?,
    error: String?,
    onSend: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add a friend", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Enter your friend's exact chat username. We’ll find their profile and send the request.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Search username") },
                placeholder = { Text("@player_name") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!busy && username.isNotBlank()) onSend() }),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { FeedbackBanner(it, isError = true) }
            notice?.let { FeedbackBanner(it, isError = false) }
            Button(
                onClick = onSend,
                enabled = !busy && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Send friend request")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    ownMessage: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var actionsExpanded by remember(message.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (ownMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (ownMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.editedAt != null) {
                        Text(
                            "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        message.createdAt.substringAfter('T').take(5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (ownMessage) {
            Spacer(Modifier.width(2.dp))
            Box {
                IconButton(
                    onClick = { actionsExpanded = true },
                    enabled = !busy,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("⋮", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit message") },
                        enabled = !busy,
                        onClick = {
                            actionsExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete message", color = MaterialTheme.colorScheme.error) },
                        enabled = !busy,
                        onClick = {
                            actionsExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMessageDialog(
    message: ChatMessage,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(message.id) { mutableStateOf(message.body) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Edit message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your friend will see the updated message.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 2000) text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    enabled = !busy
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = !busy && text.isNotBlank()) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteMessageDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete this message?") },
        text = { Text("This removes the message from the conversation and can’t be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun FriendAvatar(
    name: String,
    presence: ChatPresence? = null,
    hasUnread: Boolean = false,
    size: Dp = 36.dp
) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(modifier = Modifier.size(size)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(initial, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (presence != null) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp)
                    .background(presenceColor(presence), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        if (hasUnread) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).size(12.dp)
                    .background(Color(0xFFFFA126), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
private fun PresenceLabel(presence: ChatPresence) {
    val tint = presenceColor(presence)
    Surface(color = tint.copy(alpha = 0.12f), contentColor = tint, shape = CircleShape) {
        Text(
            presence.label(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun ChatPresence.label(): String = when (this) {
    ChatPresence.ONLINE -> "Online"
    ChatPresence.OFFLINE -> "Offline"
    ChatPresence.INACTIVE -> "Inactive 48h+"
}

@Composable
private fun presenceColor(presence: ChatPresence): Color = when (presence) {
    ChatPresence.ONLINE -> Color(0xFF2DBD72)
    ChatPresence.OFFLINE -> MaterialTheme.colorScheme.outline
    ChatPresence.INACTIVE -> MaterialTheme.colorScheme.error
}

@Composable
private fun EmptyChatPanel(text: String, onAddFriend: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("DM", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Text("Your community, one message away", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onAddFriend) { Text("Find a friend") }
        }
    }
}

@Composable
private fun EmptySidePanel(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeedbackBanner(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.10f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}
