/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery and contributors
 * Licensed under the GNU GPL v3 or later.
 */

package com.movtery.zalithlauncher.ui.screens.content

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.chat.ChatController
import com.movtery.zalithlauncher.chat.ChatFriend
import com.movtery.zalithlauncher.chat.ChatMessage
import com.movtery.zalithlauncher.chat.ChatUiState
import com.movtery.zalithlauncher.chat.PartyDraft
import com.movtery.zalithlauncher.chat.PartyInvite
import com.movtery.zalithlauncher.chat.PartyMessage
import com.movtery.zalithlauncher.chat.PartyUiState
import com.movtery.zalithlauncher.chat.SocialPartiesController
import com.movtery.zalithlauncher.chat.SocialParty
import com.movtery.zalithlauncher.chat.SocialPresence
import com.movtery.zalithlauncher.chat.lastSeenText
import com.movtery.zalithlauncher.chat.socialPresence
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Social hub: friends, direct messages, parties and invites.
 *
 * The DM/friendship layer is served by [ChatController] (existing chat backend); parties are served
 * by [SocialPartiesController] on top of the party tables added in
 * `supabase/migrations/20261103090000_chat_parties.sql`.
 */

private enum class SocialTab(val label: String) {
    FRIENDS("Friends"),
    CHATS("Chats"),
    PARTIES("Parties"),
    REQUESTS("Requests")
}

@Composable
fun SocialHubScreen(backStackViewModel: ScreenBackStackViewModel) {
    val context = LocalContext.current
    val launcherAccount by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val controllerKey = launcherAccount?.let {
        "${it.uniqueUUID}:${it.accountType}:${it.username}:${it.profileId}:${it.accessToken.hashCode()}"
    }
    val chat = remember(controllerKey) { ChatController(context.applicationContext, launcherAccount) }
    val chatState by chat.state.collectAsStateWithLifecycle()

    val session = chatState.session
    val parties = remember(session?.userId, session?.accessToken) {
        session?.let { SocialPartiesController(it) }
    }
    val emptyPartyState = remember { MutableStateFlow(PartyUiState()).asStateFlow() }
    val partyStateFlow = parties?.state ?: emptyPartyState
    val partyState by partyStateFlow.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    var restoringProfile by rememberSaveable(controllerKey) { mutableStateOf(false) }

    LaunchedEffect(chat) { chat.start() }
    LaunchedEffect(parties) { parties?.start() }
    DisposableEffect(chat, parties) {
        onDispose {
            chat.close()
            parties?.close()
        }
    }

    BaseScreen(
        screenKey = NormalNavKey.Chat,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { isVisible ->
        LaunchedEffect(chat, parties, isVisible, lifecycleOwner) {
            if (!isVisible) {
                chat.setScreenVisible(false)
                parties?.setScreenVisible(false)
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                chat.setScreenVisible(true)
                parties?.setScreenVisible(true)
                try {
                    awaitCancellation()
                } finally {
                    chat.setScreenVisible(false)
                    parties?.setScreenVisible(false)
                }
            }
        }

        val account = launcherAccount
        when {
            account == null -> SocialAccountRequired(
                onOpenAccounts = { backStackViewModel.mainScreen.navigateTo(NormalNavKey.AccountManager()) }
            )

            chatState.busy && chatState.profile == null && !chatState.needsProfile ->
                SocialLoading("Connecting your social profile…")

            restoringProfile -> SocialProfileRecovery(
                state = chatState,
                chat = chat,
                onCancel = { restoringProfile = false }
            )

            chatState.needsProfile -> SocialProfileSetup(
                state = chatState,
                chat = chat,
                account = account,
                onRecover = { restoringProfile = true }
            )

            chatState.session == null || (chatState.error != null && chatState.profile == null) ->
                SocialOffline(
                    message = chatState.error ?: "Chat is not connected yet.",
                    onRetry = { chat.retry() },
                    onOpenAccounts = { backStackViewModel.mainScreen.navigateTo(NormalNavKey.AccountManager()) }
                )

            else -> SocialHub(
                account = account,
                chat = chat,
                chatState = chatState,
                parties = parties,
                partyState = partyState
            )
        }
    }
}

// ------------------------------------------------------------------ gates

@Composable
private fun SocialAccountRequired(onOpenAccounts: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        SocialCard(Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Social needs a launcher account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Pick a Microsoft or offline account first. Your social profile, friends and parties are tied to it automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenAccounts) { Text("Open Account Manager") }
            }
        }
    }
}

@Composable
private fun SocialLoading(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SocialOffline(message: String, onRetry: () -> Unit, onOpenAccounts: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        SocialCard(Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Social is offline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetry) { Text("Retry") }
                    OutlinedButton(onClick = onOpenAccounts) { Text("Accounts") }
                }
            }
        }
    }
}

@Composable
private fun SocialProfileSetup(
    state: ChatUiState,
    chat: ChatController,
    account: Account,
    onRecover: () -> Unit
) {
    var username by rememberSaveable(state.suggestedUsername) { mutableStateOf(state.suggestedUsername) }
    val recoveryCode = state.recoveryCode

    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        SocialCard(Modifier.widthIn(max = 540.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Claim your social handle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Friends find you by @username. Your Minecraft name stays private until you share it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(20) },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    supportingText = { Text("3-20 letters, numbers, dots or underscores") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (recoveryCode != null) {
                    Text("Save this recovery code", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    CopyBlock(value = recoveryCode, label = "Recovery code")
                    Text(
                        "It is the only way to get this handle back on a new device. It is never stored on the device after you leave this screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { chat.dismissRecoveryCode() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                } else {
                    state.error?.let { SocialErrorText(it) }
                    Button(
                        onClick = { chat.completeProfile(username) },
                        enabled = !state.busy && username.length >= 3,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create my profile") }
                    TextButton(onClick = onRecover) { Text("I already have a handle — recover it") }
                    Text(
                        "Signed in as ${account.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialProfileRecovery(state: ChatUiState, chat: ChatController, onCancel: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        SocialCard(Modifier.widthIn(max = 540.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recover your handle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Recovery code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { SocialErrorText(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { chat.recoverProfile(username, code) },
                        enabled = !state.busy && username.isNotBlank() && code.isNotBlank()
                    ) { Text("Recover") }
                    OutlinedButton(onClick = onCancel) { Text("Back") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ hub

@Composable
private fun SocialHub(
    account: Account,
    chat: ChatController,
    chatState: ChatUiState,
    parties: SocialPartiesController?,
    partyState: PartyUiState
) {
    var tab by rememberSaveable { mutableStateOf(SocialTab.FRIENDS) }
    var showCreateParty by rememberSaveable { mutableStateOf(false) }
    var showJoinParty by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var openThread by rememberSaveable { mutableStateOf<String?>(null) }

    val profile = chatState.profile
    val me = parties?.selfUserId
    val friends = remember(chatState.friends) {
        chatState.friends.map { friend -> friend }
            .sortedWith(compareByDescending<ChatFriend> { socialPresence(it.lastActiveAtMillis) == SocialPresence.ONLINE })
    }
    val onlineFriends = friends.filter { socialPresence(it.lastActiveAtMillis) == SocialPresence.ONLINE }
    val offlineFriends = friends.filterNot { socialPresence(it.lastActiveAtMillis) == SocialPresence.ONLINE }
    val requestsBadge = chatState.incomingRequests.size + partyState.invites.size
    val friend = chatState.friends.firstOrNull { it.userId == openThread }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("header") {
                SocialHeader(
                    displayName = profile?.displayName ?: profile?.username ?: account.username,
                    username = profile?.username.orEmpty(),
                    bio = profile?.bio.orEmpty(),
                    friendCount = friends.size,
                    activeParty = partyState.activeParty,
                    onEditProfile = { showProfile = true }
                )
            }

            item("tabs") {
                SocialTabBar(
                    selected = tab,
                    requestsBadge = requestsBadge,
                    onSelect = { tab = it }
                )
            }

            (chatState.error ?: partyState.error)?.let { error ->
                item("error") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = {
                                chat.clearFeedback()
                                parties?.dismissError()
                            }) { Text("Dismiss") }
                        }
                    }
                }
            }

            partyState.notice?.let { notice ->
                item("notice") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                notice,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = { parties?.clearFeedback() }) { Text("OK") }
                        }
                    }
                }
            }

            when (tab) {
                SocialTab.FRIENDS -> {
                    item("party-banner") {
                        PartyBanner(
                            party = partyState.activeParty,
                            onCreate = { showCreateParty = true },
                            onJoinByCode = { showJoinParty = true },
                            onOpen = { tab = SocialTab.PARTIES }
                        )
                    }
                    if (friends.isEmpty()) {
                        item("friends-empty") {
                            SocialEmpty(
                                title = "No friends yet",
                                body = "Add someone by their @username — you can then chat, invite them to parties and join each other's worlds.",
                                action = "Add a friend",
                                onAction = { tab = SocialTab.REQUESTS }
                            )
                        }
                    } else {
                        if (onlineFriends.isNotEmpty()) {
                            item("online-title") { SocialSectionTitle("Online", onlineFriends.size) }
                            items(onlineFriends, key = { "online-${it.userId}" }) { friendRow ->
                                FriendRow(
                                    friend = friendRow,
                                    canInvite = partyState.activeParty != null,
                                    onChat = { openThread = friendRow.userId },
                                    onInvite = { parties?.invite(friendRow.username) }
                                )
                            }
                        }
                        if (offlineFriends.isNotEmpty()) {
                            item("offline-title") { SocialSectionTitle("Offline", offlineFriends.size) }
                            items(offlineFriends, key = { "offline-${it.userId}" }) { friendRow ->
                                FriendRow(
                                    friend = friendRow,
                                    canInvite = partyState.activeParty != null,
                                    onChat = { openThread = friendRow.userId },
                                    onInvite = { parties?.invite(friendRow.username) }
                                )
                            }
                        }
                    }
                }

                SocialTab.CHATS -> {
                    if (friends.isEmpty()) {
                        item("chats-empty") {
                            SocialEmpty(
                                title = "No conversations yet",
                                body = "Direct messages unlock as soon as you and another player are friends.",
                                action = "Find friends",
                                onAction = { tab = SocialTab.REQUESTS }
                            )
                        }
                    } else {
                        items(friends, key = { "chat-${it.userId}" }) { friendRow ->
                            ChatListRow(
                                friend = friendRow,
                                onOpen = { openThread = friendRow.userId }
                            )
                        }
                    }
                }

                SocialTab.PARTIES -> {
                    item("party-panel") {
                        PartyPanel(
                            party = partyState.activeParty,
                            me = me,
                            busy = partyState.busy,
                            onOpenChat = { openThread = PARTY_THREAD },
                            onInvite = { username -> parties?.invite(username) },
                            onLeave = { parties?.leaveParty() },
                            onEnd = { parties?.endParty() },
                            onExtend = { parties?.extendParty() },
                            onKick = { userId -> parties?.kickMember(userId) },
                            onCreate = { showCreateParty = true },
                            onJoinByCode = { showJoinParty = true }
                        )
                    }
                    if (partyState.publicParties.isNotEmpty()) {
                        item("public-title") { SocialSectionTitle("Discover parties", partyState.publicParties.size) }
                        items(partyState.publicParties, key = { "public-${it.id}" }) { party ->
                            PublicPartyCard(
                                party = party,
                                joining = partyState.busy,
                                onJoin = { parties?.joinParty(party.id) }
                            )
                        }
                    }
                }

                SocialTab.REQUESTS -> {
                    if (requestsBadge == 0 && partyState.sentInvites.isEmpty()) {
                        item("requests-empty") {
                            SocialEmpty(
                                title = "Nothing waiting",
                                body = "Friend requests and party invites show up here.",
                                action = null,
                                onAction = {}
                            )
                        }
                    }
                    if (partyState.invites.isNotEmpty()) {
                        item("invites-title") { SocialSectionTitle("Party invites", partyState.invites.size) }
                        items(partyState.invites, key = { "invite-${it.id}" }) { invite ->
                            PartyInviteCard(
                                invite = invite,
                                onAccept = { parties?.respondToInvite(invite, true) },
                                onDecline = { parties?.respondToInvite(invite, false) }
                            )
                        }
                    }
                    if (chatState.incomingRequests.isNotEmpty()) {
                        item("requests-title") { SocialSectionTitle("Friend requests", chatState.incomingRequests.size) }
                        items(chatState.incomingRequests, key = { "req-${it.id}" }) { request ->
                            FriendRequestCard(
                                displayName = request.displayName,
                                username = request.username,
                                onAccept = { chat.respondToRequest(request, true) },
                                onDecline = { chat.respondToRequest(request, false) }
                            )
                        }
                    }
                    item("add-friend") { AddFriendCard(chat = chat, state = chatState) }
                }
            }
        }

        if (partyState.activeParty == null && tab != SocialTab.REQUESTS) {
            Button(
                onClick = { showCreateParty = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Icon(painterResource(R.drawable.ic_add), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New party")
            }
        }

        // DM thread
        if (friend != null) {
            DmThread(
                friend = friend,
                state = chatState,
                chat = chat,
                onClose = {
                    openThread = null
                }
            )
        }

        // Party thread
        if (openThread == PARTY_THREAD) {
            PartyThread(
                party = partyState.activeParty,
                messages = partyState.messages,
                selfUserId = me,
                sending = partyState.sending,
                onSend = { parties?.sendMessage(it) },
                onDelete = { parties?.deleteMessage(it) },
                onClose = { openThread = null }
            )
        }
    }

    chatState.recoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = { chat.dismissRecoveryCode() },
            title = { Text("Save your recovery code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This code restores your @handle on a new device. Keep it somewhere safe — it is shown only once.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    CopyBlock(value = code, label = "Recovery code")
                }
            },
            confirmButton = {
                Button(onClick = { chat.dismissRecoveryCode() }) { Text("I saved it") }
            }
        )
    }

    if (showCreateParty && parties != null) {
        CreatePartyDialog(
            onDismiss = { showCreateParty = false },
            onCreate = { draft ->
                parties.createParty(draft) {
                    showCreateParty = false
                    tab = SocialTab.PARTIES
                }
            }
        )
    }

    if (showJoinParty && parties != null) {
        JoinPartyDialog(
            onDismiss = { showJoinParty = false },
            onJoin = { code ->
                parties.joinByCode(code) {
                    showJoinParty = false
                    tab = SocialTab.PARTIES
                }
            }
        )
    }

    if (showProfile) {
        EditProfileDialog(
            currentUsername = profile?.username.orEmpty(),
            currentDisplayName = profile?.displayName.orEmpty(),
            currentBio = profile?.bio.orEmpty(),
            onDismiss = { showProfile = false },
            onSave = { username, displayName, bio ->
                chat.updateProfile(username, displayName, bio)
                showProfile = false
            },
            onCreateRecoveryCode = {
                chat.createRecoveryCode()
                showProfile = false
            }
        )
    }

}

private const val PARTY_THREAD = "__party__"

// ------------------------------------------------------------------ header + tabs

@Composable
private fun SocialHeader(
    displayName: String,
    username: String,
    bio: String,
    friendCount: Int,
    activeParty: SocialParty?,
    onEditProfile: () -> Unit
) {
    val gradient = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    )
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .background(gradient)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(name = displayName, size = 54.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (username.isNotBlank()) "@$username" else "No handle yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bio.isNotBlank()) {
                        Text(
                            bio,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onEditProfile) {
                    Icon(painterResource(R.drawable.ic_edit_outlined), contentDescription = "Edit profile")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialStatPill("${friendCount} friends")
                if (activeParty != null) {
                    SocialStatPill("${activeParty.memberCount}/${activeParty.maxMembers} in party", highlight = true)
                } else {
                    SocialStatPill("Not in a party")
                }
            }
        }
    }
}

@Composable
private fun SocialStatPill(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SocialTabBar(selected: SocialTab, requestsBadge: Int, onSelect: (SocialTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SocialTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val badge = if (tab == SocialTab.REQUESTS) requestsBadge else 0
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(tab) }
            ) {
                Column(
                    Modifier.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (badge > 0) "${tab.label} ($badge)" else tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ friends

@Composable
private fun FriendRow(
    friend: ChatFriend,
    canInvite: Boolean,
    onChat: () -> Unit,
    onInvite: () -> Unit
) {
    val presence = socialPresence(friend.lastActiveAtMillis)
    SocialCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onChat)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PresenceAvatar(name = friend.displayName, presence = presence)
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "@${friend.username} · ${presenceLabel(friend.lastActiveAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (canInvite) {
                FilledTonalButton(onClick = onInvite, shape = RoundedCornerShape(50)) {
                    Text("Invite", style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onChat) {
                Icon(painterResource(R.drawable.ic_chat_bubble), contentDescription = "Message")
            }
        }
    }
}

@Composable
private fun ChatListRow(friend: ChatFriend, onOpen: () -> Unit) {
    val presence = socialPresence(friend.lastActiveAtMillis)
    SocialCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PresenceAvatar(name = friend.displayName, presence = presence)
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    presenceLabel(friend.lastActiveAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(painterResource(R.drawable.ic_chat_bubble), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun presenceLabel(lastActiveAtMillis: Long?): String = when (socialPresence(lastActiveAtMillis)) {
    SocialPresence.ONLINE -> "Online"
    SocialPresence.IDLE -> "Away"
    SocialPresence.OFFLINE -> lastSeenText(lastActiveAtMillis)
}

// ------------------------------------------------------------------ parties

@Composable
private fun PartyBanner(
    party: SocialParty?,
    onCreate: () -> Unit,
    onJoinByCode: () -> Unit,
    onOpen: () -> Unit
) {
    SocialCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (party == null) {
                Text("Play together", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Start a party, share a 6-character code, and everyone jumps into the same instance together.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCreate) { Text("Create party") }
                    OutlinedButton(onClick = onJoinByCode) { Text("Join with code") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(party.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(party.summaryTag(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${party.memberCount}/${party.maxMembers}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    party.members.take(6).forEach { member ->
                        PresenceAvatar(name = member.displayName, presence = member.presence(), size = 28.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CopyBlock(value = party.inviteCode, label = "Party code", compact = true)
                    TextButton(onClick = onOpen) { Text("Open") }
                }
            }
        }
    }
}

@Composable
private fun PartyPanel(
    party: SocialParty?,
    me: String?,
    busy: Boolean,
    onOpenChat: () -> Unit,
    onInvite: (String) -> Unit,
    onLeave: () -> Unit,
    onEnd: () -> Unit,
    onExtend: () -> Unit,
    onKick: (String) -> Unit,
    onCreate: () -> Unit,
    onJoinByCode: () -> Unit
) {
    if (party == null) {
        SocialCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You are not in a party", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Host a party with your current instance, or join a friend's party with a code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCreate, enabled = !busy) { Text("Create party") }
                    OutlinedButton(onClick = onJoinByCode, enabled = !busy) { Text("Join with code") }
                }
            }
        }
        return
    }

    var inviteName by rememberSaveable(party.id) { mutableStateOf("") }
    val isHost = party.isHost(me)
    var menuFor by remember { mutableStateOf<String?>(null) }

    SocialCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(party.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(party.summaryTag(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        "${party.memberCount}/${party.maxMembers}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CopyBlock(value = party.inviteCode, label = "Party code", compact = true)
                Column {
                    Text(party.remainingText(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (party.isPublic) "Public party" else "Private party",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!party.serverAddress.isNullOrBlank()) {
                Text("Server: ${party.serverAddress}", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            Text("Members", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            party.members.forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresenceAvatar(name = member.displayName, presence = member.presence(), size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (member.userId == me) "${member.displayName} (you)" else member.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            if (member.isHost) "Host · @${member.username}" else "@${member.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isHost && member.userId != me) {
                        Box {
                            IconButton(onClick = { menuFor = member.userId }) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Member actions")
                            }
                            DropdownMenu(expanded = menuFor == member.userId, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(
                                    text = { Text("Remove from party") },
                                    onClick = {
                                        menuFor = null
                                        onKick(member.userId)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (!party.isFull) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it.trim().take(20) },
                        label = { Text("Invite @username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inviteName.isNotBlank()) {
                                onInvite(inviteName)
                                inviteName = ""
                            }
                        })
                    )
                    Button(
                        onClick = {
                            if (inviteName.isNotBlank()) {
                                onInvite(inviteName)
                                inviteName = ""
                            }
                        },
                        enabled = inviteName.isNotBlank()
                    ) { Text("Invite") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_chat_bubble), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Party chat")
                }
                if (isHost) {
                    OutlinedButton(onClick = onExtend) { Text("+1h") }
                    OutlinedButton(onClick = onEnd) { Text("End") }
                } else {
                    OutlinedButton(onClick = onLeave) { Text("Leave") }
                }
            }
        }
    }
}

@Composable
private fun PublicPartyCard(party: SocialParty, joining: Boolean, onJoin: () -> Unit) {
    SocialCard {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InitialsAvatar(name = party.title, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(party.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(party.summaryTag(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    "Host ${party.hostMember()?.displayName ?: "Player"} · ${party.memberCount}/${party.maxMembers} · ${party.remainingText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Button(onClick = onJoin, enabled = !joining && !party.isFull) {
                Text(if (party.isFull) "Full" else "Join")
            }
        }
    }
}

@Composable
private fun PartyInviteCard(invite: PartyInvite, onAccept: () -> Unit, onDecline: () -> Unit) {
    SocialCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InitialsAvatar(name = invite.fromDisplayName, size = 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "${invite.fromDisplayName} invited you to ${invite.partyTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        invite.summaryTag(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAccept) { Text("Join party") }
                OutlinedButton(onClick = onDecline) { Text("Decline") }
            }
        }
    }
}

@Composable
private fun FriendRequestCard(displayName: String, username: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    SocialCard {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InitialsAvatar(name = displayName, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("@$username wants to be friends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAccept) { Text("Accept") }
            OutlinedButton(onClick = onDecline) { Text("Decline") }
        }
    }
}

@Composable
private fun AddFriendCard(chat: ChatController, state: ChatUiState) {
    var username by rememberSaveable { mutableStateOf("") }
    SocialCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add a friend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Type their social handle (not the Minecraft name).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim().take(20) },
                    label = { Text("@username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.None),
                    keyboardActions = KeyboardActions(onSend = {
                        if (username.isNotBlank()) {
                            chat.addFriend(username)
                            username = ""
                        }
                    })
                )
                Button(
                    onClick = {
                        chat.addFriend(username)
                        username = ""
                    },
                    enabled = username.length >= 3 && !state.busy
                ) { Text("Send") }
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun CreatePartyDialog(onDismiss: () -> Unit, onCreate: (PartyDraft) -> Unit) {
    val versions by VersionsManager.versions.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var serverAddress by rememberSaveable { mutableStateOf("") }
    var maxMembers by rememberSaveable { mutableStateOf(4) }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    var versionMenu by remember { mutableStateOf(false) }

    val version: Version? = remember(selected, versions) { versions.firstOrNull { it.getVersionName() == selected } }
    val defaultTitle = version?.getVersionName()?.let { "Playing $it" } ?: "Minecraft session"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a party") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(48) },
                    label = { Text("Party name") },
                    placeholder = { Text(defaultTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    OutlinedButton(onClick = { versionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            version?.let { "${it.getVersionName()} · ${it.getVersionInfo()?.minecraftVersion.orEmpty()}" }
                                ?: (if (versions.isEmpty()) "No versions installed" else "Choose an instance"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = versionMenu, onDismissRequest = { versionMenu = false }) {
                        versions.forEach { entry ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${entry.getVersionName()} · ${entry.getVersionInfo()?.minecraftVersion.orEmpty()}",
                                        maxLines = 1
                                    )
                                },
                                onClick = {
                                    selected = entry.getVersionName()
                                    versionMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = { serverAddress = it.take(80) },
                    label = { Text("Server address (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Max players: $maxMembers", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6, 8, 12, 16).forEach { size ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (maxMembers == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { maxMembers = size }
                        ) {
                            Text(
                                "$size",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (maxMembers == size) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public party (anyone can find it)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val info = version?.getVersionInfo()
                    onCreate(
                        PartyDraft(
                            title = title.ifBlank { defaultTitle },
                            versionId = version?.getVersionName(),
                            instanceName = version?.getVersionName(),
                            mcVersion = info?.minecraftVersion,
                            loader = info?.primaryLoader?.loader?.displayName,
                            serverAddress = serverAddress,
                            maxMembers = maxMembers,
                            isPublic = isPublic
                        )
                    )
                },
                enabled = versions.isNotEmpty() || selected != null
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun JoinPartyDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a party") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ask the host for their 6-character party code.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = { Text("Party code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onJoin(code) }, enabled = code.length >= 6) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditProfileDialog(
    currentUsername: String,
    currentDisplayName: String,
    currentBio: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onCreateRecoveryCode: () -> Unit = {}
) {
    var username by rememberSaveable { mutableStateOf(currentUsername) }
    var displayName by rememberSaveable { mutableStateOf(currentDisplayName) }
    var bio by rememberSaveable { mutableStateOf(currentBio) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit social profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(32) },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(20) },
                    label = { Text("Username") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(120) },
                    label = { Text("Bio") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onCreateRecoveryCode) { Text("Generate a new recovery code") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(username, displayName, bio) },
                enabled = username.length >= 3
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ threads

@Composable
private fun DmThread(
    friend: ChatFriend,
    state: ChatUiState,
    chat: ChatController,
    onClose: () -> Unit
) {
    var draft by rememberSaveable(friend.userId) { mutableStateOf("") }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(friend.userId) { chat.selectFriend(friend) }

    ThreadScaffold(
        title = friend.displayName,
        subtitle = presenceLabel(friend.lastActiveAtMillis),
        onClose = onClose,
        messages = state.messages.map { message ->
            ThreadMessage(
                id = message.id,
                mine = message.senderId == state.profile?.id,
                author = if (message.senderId == state.profile?.id) "You" else friend.displayName,
                body = message.body,
                time = formatChatTime(message.createdAt),
                pending = false,
                edited = message.editedAt != null
            )
        },
        composerValue = draft,
        onComposerChange = { draft = it },
        onSend = {
            if (draft.isNotBlank()) {
                chat.sendMessage(draft)
                draft = ""
            }
        },
        sending = state.sendingMessage,
        loading = state.busy && state.messages.isEmpty(),
        onEdit = { id ->
            state.messages.firstOrNull { it.id == id }?.let {
                editing = it
                editText = it.body
            }
        },
        onDelete = { id -> state.messages.firstOrNull { it.id == id }?.let { chat.deleteMessage(it) } }
    )

    editing?.let { message ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editText.isNotBlank()) chat.editMessage(message, editText)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PartyThread(
    party: SocialParty?,
    messages: List<PartyMessage>,
    selfUserId: String?,
    sending: Boolean,
    onSend: (String) -> Unit,
    onDelete: (PartyMessage) -> Unit,
    onClose: () -> Unit
) {
    var draft by rememberSaveable(party?.id) { mutableStateOf("") }
    ThreadScaffold(
        title = party?.title ?: "Party chat",
        subtitle = party?.let { "${it.memberCount}/${it.maxMembers} · ${it.remainingText()}" } ?: "",
        onClose = onClose,
        messages = messages.map { message ->
            ThreadMessage(
                id = message.id,
                mine = message.senderId == selfUserId,
                author = if (message.senderId == selfUserId) "You" else message.senderName,
                body = message.body,
                time = formatMillis(message.createdAtMillis),
                pending = message.pending,
                edited = false
            )
        },
        composerValue = draft,
        onComposerChange = { draft = it },
        onSend = {
            if (draft.isNotBlank()) {
                onSend(draft)
                draft = ""
            }
        },
        sending = sending,
        loading = false,
        onEdit = null,
        onDelete = { id -> messages.firstOrNull { it.id == id }?.let(onDelete) }
    )
}

private data class ThreadMessage(
    val id: String,
    val mine: Boolean,
    val author: String,
    val body: String,
    val time: String,
    val pending: Boolean,
    val edited: Boolean
)

@Composable
private fun ThreadScaffold(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    messages: List<ThreadMessage>,
    composerValue: String,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    loading: Boolean,
    onEdit: ((String) -> Unit)?,
    onDelete: ((String) -> Unit)?
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            HorizontalDivider()

            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Say hi 👋", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Messages are private to this chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onEdit = onEdit,
                            onDelete = onDelete
                        )
                    }
                }
            }

            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = composerValue,
                    onValueChange = onComposerChange,
                    placeholder = { Text("Message") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Button(onClick = onSend, enabled = composerValue.isNotBlank() && !sending) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ThreadMessage, onEdit: ((String) -> Unit)?, onDelete: ((String) -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            if (!message.mine) {
                Text(
                    message.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.mine) 16.dp else 4.dp,
                    bottomEnd = if (message.mine) 4.dp else 16.dp
                ),
                color = if (message.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.clickable(enabled = onEdit != null || onDelete != null) { menu = true }
            ) {
                Text(
                    message.body,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(message.time)
                        if (message.edited) append(" · edited")
                        if (message.pending) append(" · sending")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (message.mine && onEdit != null) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menu = false
                            onEdit(message.id)
                        }
                    )
                }
                if (message.mine && onDelete != null) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menu = false
                            onDelete(message.id)
                        }
                    )
                }
                if (!message.mine) {
                    DropdownMenuItem(text = { Text("Close") }, onClick = { menu = false })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small pieces

@Composable
private fun SocialCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column { content() }
    }
}

@Composable
private fun SocialSectionTitle(text: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SocialEmpty(title: String, body: String, action: String?, onAction: () -> Unit) {
    SocialCard {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun SocialErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initials = remember(name) {
        name.trim().split(" ", "_", ".").filter { it.isNotBlank() }.take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
    }
    val hue = remember(name) { (name.hashCode() % 360 + 360) % 360 }
    val color = Color.hsl(hue.toFloat(), 0.45f, 0.45f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.6f).sp
        )
    }
}

@Composable
private fun PresenceAvatar(name: String, presence: SocialPresence, size: androidx.compose.ui.unit.Dp = 42.dp) {
    Box {
        InitialsAvatar(name = name, size = size)
        val dotColor = when (presence) {
            SocialPresence.ONLINE -> Color(0xFF3FBF6F)
            SocialPresence.IDLE -> Color(0xFFE0A73C)
            SocialPresence.OFFLINE -> Color(0xFF7A828B)
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun CopyBlock(value: String, label: String, compact: Boolean = false) {
    val context = LocalContext.current
    var copied by remember(value) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.clickable {
            context.copyToClipboard(value)
            copied = true
        }
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (copied) "Copied!" else value.ifBlank { "------" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                painterResource(R.drawable.ic_copy_all_outlined),
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    LaunchedEffect(copied, context) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("TurboCraft", text))
}

private fun formatChatTime(raw: String): String = runCatching {
    val instant = OffsetDateTime.parse(raw).toInstant()
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault("")

private fun formatMillis(millis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(millis))
}.getOrDefault("")
