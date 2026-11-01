package com.unera.social.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import com.unera.social.data.GroupMemberInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unera.social.ui.components.VerifiedBadge
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import com.unera.social.ui.components.FeatheredAvatar
import com.unera.social.ui.components.PostUploadBottomPill
import com.unera.social.ui.components.PostUploadState
import coil.compose.AsyncImage
import com.unera.social.model.*
import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.Send
import com.unera.social.ui.components.DiscussionsSheetDialog
import com.unera.social.ui.components.GalleryViewerDialog
import com.unera.social.ui.components.ShareBottomSheetDialog
import com.unera.social.ui.components.ReactionsSheetDialog
import com.unera.social.ui.components.DiscussButton
import com.unera.social.ui.components.REACTION_EMOJIS
import com.unera.social.ui.components.getReactionEmoji

fun isCareerCategory(category: String?, name: String? = null): Boolean {
    val c = category?.lowercase() ?: ""
    val n = name?.lowercase() ?: ""
    return c == "recruitment" || c == "career" || c == "careers" || c == "job" || c == "jobs" ||
            n.contains("career") || n.contains("job") || n.contains("recruitment") || n.contains("hiring") || n.contains("employment")
}

fun GroupPost.toPost(group: Group? = null): Post {
    return Post(
        id = id,
        author = author,
        content = content,
        mediaUrl = imageUrls.firstOrNull(),
        mediaUrls = imageUrls,
        timestamp = createdAt,
        likesCount = likesCount,
        commentsCount = commentsCount,
        sharesCount = sharesCount,
        isLiked = isLiked,
        myReaction = myReaction,
        groupId = groupId,
        groupName = group?.name,
        groupImageUrl = group?.profileImageUrl,
        jobTitle = jobTitle,
        company = company,
        jobType = jobType,
        salary = salary,
        applicationValue = applicationValue,
        comments = comments,
        postType = "group_post",
        createdAt = createdAt
    )
}

enum class GroupsTab(val label: String) {
    YOUR_GROUPS("Your groups"),
    DISCOVER("Discover"),
    INVITES("Invites")
}

enum class GroupDetailTab(val label: String) {
    DISCUSSION("Discussion"),
    EVENTS("Events"),
    MEMBERS("Members"),
    ABOUT("About")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    currentUser: User,
    groups: List<Group>,
    groupPosts: List<GroupPost>,
    groupInvites: List<GroupInvite>,
    allEvents: List<Event>,
    allUsers: List<User>,
    initialGroupId: Long? = null,
    groupSuggestions: List<Group> = emptyList(),
    groupMembers: List<GroupMemberInfo> = emptyList(),
    groupEvents: List<Event> = emptyList(),
    isGroupsLoading: Boolean = false,
    isGroupPostsLoading: Boolean = false,
    onBack: () -> Unit,
    onSelectGroup: (Long?) -> Unit = {},
    onCreateGroup: (name: String, desc: String, type: String, category: String, profile: String, cover: String) -> Unit,
    onJoinGroup: (Long) -> Unit,
    onLeaveGroup: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onUpdateGroupSettings: (Long, String, String, Boolean) -> Unit,
    onPostToGroup: (Long, String, List<String>, Double?, String, String?, String?, String, String?, String?, String?, String?, String?, String?) -> Unit,
    onToggleLikePost: (Long, String) -> Unit,
    onAddPostComment: (Long, String) -> Unit,
    onDeletePost: (Long) -> Unit,
    onSharePost: (Long) -> Unit,
    onInviteUser: (Long, Long) -> Unit,
    onAcceptInvite: (Long, Long) -> Unit,
    onDeclineInvite: (Long) -> Unit,
    onToggleMemberPosting: (Long, Long, Boolean) -> Unit,
    onSetMemberRole: (Long, Long, String) -> Unit,
    onRemoveMember: (Long, Long) -> Unit,
    onProfileClick: (Long) -> Unit,
    uploadState: PostUploadState? = null,
    onDismissUpload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeGroupId by remember { mutableStateOf(initialGroupId) }
    var selectedTopTab by remember { mutableStateOf(GroupsTab.YOUR_GROUPS) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Most visited") }
    var showSortDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeGroupId) {
        onSelectGroup(activeGroupId)
    }

    // Modals
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var selectedCategoryForCreate by remember { mutableStateOf("general") }

    val context = LocalContext.current
    val activeGroup = groups.find { it.id == activeGroupId }

    BackHandler {
        if (activeGroupId != null) {
            activeGroupId = null
        } else {
            onBack()
        }
    }

    if (activeGroup != null) {
        val effectiveEvents = if (groupEvents.isNotEmpty()) groupEvents else allEvents.filter { it.groupId == activeGroup.id }
        GroupDetailScreen(
            group = activeGroup,
            currentUser = currentUser,
            allPosts = groupPosts.filter { it.groupId == activeGroup.id },
            allEvents = effectiveEvents,
            allUsers = allUsers,
            groupMembersInfo = groupMembers,
            isPostsLoading = isGroupPostsLoading,
            onBack = { activeGroupId = null },
            onJoinGroup = { onJoinGroup(activeGroup.id) },
            onLeaveGroup = { onLeaveGroup(activeGroup.id) },
            onDeleteGroup = {
                onDeleteGroup(activeGroup.id)
                activeGroupId = null
            },
            onUpdateSettings = { name, desc, postingAllowed ->
                onUpdateGroupSettings(activeGroup.id, name, desc, postingAllowed)
            },
            onPost = { content, images, price, curr, cond, loc, stat, job, comp, jType, sal, appType, appVal ->
                onPostToGroup(activeGroup.id, content, images, price, curr, cond, loc, stat, job, comp, jType, sal, appType, appVal)
            },
            onToggleLikePost = onToggleLikePost,
            onAddPostComment = onAddPostComment,
            onDeletePost = onDeletePost,
            onSharePost = onSharePost,
            onInviteUser = { userId -> onInviteUser(activeGroup.id, userId) },
            onToggleMemberPosting = { userId, disabled -> onToggleMemberPosting(activeGroup.id, userId, disabled) },
            onSetMemberRole = { userId, role -> onSetMemberRole(activeGroup.id, userId, role) },
            onRemoveMember = { userId -> onRemoveMember(activeGroup.id, userId) },
            onProfileClick = onProfileClick,
            uploadState = uploadState,
            onDismissUpload = onDismissUpload,
            modifier = modifier
        )
    } else {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(Color(0xFF0F172A))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Text(
                                text = "Groups",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showCategoryDialog = true },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create Group", tint = Color.White)
                            }
                        }
                    }

                    // 3 Tabs: Your groups, Discover, Invites
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GroupsTab.values().forEach { tab ->
                            val selected = selectedTopTab == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) Color(0xFF1877F2) else Color(0xFF1E293B))
                                    .clickable { selectedTopTab = tab }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tab.label,
                                        color = if (selected) Color.White else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (tab == GroupsTab.INVITES && groupInvites.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFFF3425F))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${groupInvites.size}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search Groups", color = Color(0xFF64748B), fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1877F2),
                            unfocusedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                }
            },
            containerColor = Color(0xFF050B18),
            modifier = modifier
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTopTab) {
                    GroupsTab.YOUR_GROUPS -> {
                        val userGroups = groups.filter { group ->
                            val isOwner = group.adminId == currentUser.id
                            val isMem = group.isMember || group.members.contains(currentUser.id)
                            isOwner || isMem
                        }.filter {
                            searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                        }

                        val createdByMe = userGroups.filter { it.adminId == currentUser.id }
                        val joinedByMe = userGroups.filter { it.adminId != currentUser.id }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Your groups (${userGroups.size})",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Sort",
                                        color = Color(0xFF1877F2),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { showSortDialog = true }
                                    )
                                }
                            }

                            if (createdByMe.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Created by you",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(createdByMe) { group ->
                                    GroupListItem(
                                        group = group,
                                        onClick = { activeGroupId = group.id }
                                    )
                                }
                            }

                            if (joinedByMe.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Groups you joined",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(joinedByMe) { group ->
                                    GroupListItem(
                                        group = group,
                                        onClick = { activeGroupId = group.id }
                                    )
                                }
                            }

                            if (userGroups.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isGroupsLoading) {
                                            CircularProgressIndicator(color = Color(0xFF1877F2))
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(56.dp))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text("No groups yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("Create your first group or discover active ones!", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(
                                                    onClick = { showCategoryDialog = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Create Group")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    GroupsTab.DISCOVER -> {
                        val unjoinedFromGroups = groups.filter { group ->
                            val isOwner = group.adminId == currentUser.id
                            val isMem = group.isMember || group.members.contains(currentUser.id)
                            !isOwner && !isMem
                        }
                        val combinedSuggestions = (groupSuggestions + unjoinedFromGroups)
                            .distinctBy { it.id }
                            .filter {
                                searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                            }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Text(
                                    text = "Discover Groups (${combinedSuggestions.size})",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }

                            if (isGroupsLoading && combinedSuggestions.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF1877F2))
                                    }
                                }
                            } else {
                                items(combinedSuggestions) { group ->
                                    GroupListItem(
                                        group = group,
                                        onClick = { activeGroupId = group.id },
                                        showJoinButton = true,
                                        onJoinClick = { onJoinGroup(group.id) }
                                    )
                                }

                                if (combinedSuggestions.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No more groups to discover right now", color = Color(0xFF94A3B8), fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    GroupsTab.INVITES -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Text(
                                    text = "Group Invites (${groupInvites.size})",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }

                            items(groupInvites) { invite ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(16.dp),
                                    border = CardDefaults.outlinedCardBorder(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = invite.group.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${invite.group.name}" },
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(invite.group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("Invited by ${invite.inviter.name}", color = Color(0xFF45BD62), fontSize = 12.sp)
                                                Text("${invite.group.membersCount} members", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onAcceptInvite(invite.id, invite.groupId) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Accept & Join")
                                            }
                                            OutlinedButton(
                                                onClick = { onDeclineInvite(invite.id) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Decline")
                                            }
                                        }
                                    }
                                }
                            }

                            if (groupInvites.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.MailOutline, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(52.dp))
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text("No pending invites", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("When someone invites you to a group, it will show up here.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Category Selection Modal
    if (showCategoryDialog) {
        CategorySelectionDialog(
            onDismiss = { showCategoryDialog = false },
            onSelectCategory = { cat ->
                selectedCategoryForCreate = cat
                showCategoryDialog = false
                showCreateGroupDialog = true
            }
        )
    }

    // Create Group Modal
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            category = selectedCategoryForCreate,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, desc, privacy ->
                onCreateGroup(
                    name,
                    desc,
                    privacy,
                    selectedCategoryForCreate,
                    "https://ui-avatars.com/api/?name=${name}&background=random",
                    "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1500&q=80"
                )
                showCreateGroupDialog = false
            }
        )
    }

    // Sort Dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort groups", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Most visited", "Recently active", "Alphabetical").forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortMode = mode
                                    showSortDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode, color = Color.White, fontSize = 16.sp)
                            if (sortMode == mode) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1877F2))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF0F172A)
        )
    }

    if (uploadState != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            PostUploadBottomPill(
                uploadState = uploadState,
                onDismiss = onDismissUpload
            )
        }
    }
}

@Composable
fun GroupListItem(
    group: Group,
    onClick: () -> Unit,
    showJoinButton: Boolean = false,
    onJoinClick: (() -> Unit)? = null
) {
    val catColor = when (group.category) {
        "recruitment" -> Color(0xFF45BD62)
        "buy_sell" -> Color(0xFFF7B928)
        else -> Color(0xFF1877F2)
    }

    val catIcon = when (group.category) {
        "recruitment" -> Icons.Default.Work
        "buy_sell" -> Icons.Default.Storefront
        else -> Icons.Default.Groups
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = group.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${group.name}" },
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${group.membersCount} members • ${if (group.isPrivate) "Private" else "Public"}",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        if (showJoinButton && onJoinClick != null) {
            Button(
                onClick = onJoinClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Join", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: Group,
    currentUser: User,
    allPosts: List<GroupPost>,
    allEvents: List<Event>,
    allUsers: List<User>,
    groupMembersInfo: List<GroupMemberInfo> = emptyList(),
    isPostsLoading: Boolean = false,
    onBack: () -> Unit,
    onJoinGroup: () -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onUpdateSettings: (name: String, desc: String, postingAllowed: Boolean) -> Unit,
    onPost: (String, List<String>, Double?, String, String?, String?, String, String?, String?, String?, String?, String?, String?) -> Unit,
    onToggleLikePost: (Long, String) -> Unit,
    onAddPostComment: (Long, String) -> Unit,
    onDeletePost: (Long) -> Unit,
    onSharePost: (Long) -> Unit,
    onInviteUser: (Long) -> Unit,
    onToggleMemberPosting: (Long, Boolean) -> Unit,
    onSetMemberRole: (Long, String) -> Unit,
    onRemoveMember: (Long) -> Unit,
    onProfileClick: (Long) -> Unit,
    uploadState: PostUploadState? = null,
    onDismissUpload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(GroupDetailTab.DISCUSSION) }
    var showCreatePostModal by remember { mutableStateOf(false) }
    var autoLaunchPhotoPicker by remember { mutableStateOf(false) }
    var showInviteModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showShareModal by remember { mutableStateOf(false) }
    var activeDiscussionPost by remember { mutableStateOf<Post?>(null) }
    var activeReactionsPost by remember { mutableStateOf<Post?>(null) }
    var activeSharePost by remember { mutableStateOf<Post?>(null) }
    var activeGalleryState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var activePostForGallery by remember { mutableStateOf<Post?>(null) }
    val context = LocalContext.current

    val isOwner = group.adminId == currentUser.id
    val isMember = isOwner || group.isMember || group.members.contains(currentUser.id)
    val canPost = isOwner || (isMember && group.memberPostingAllowed)

    val catColor = when (group.category) {
        "recruitment" -> Color(0xFF45BD62)
        "buy_sell" -> Color(0xFFF7B928)
        else -> Color(0xFF1877F2)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050B18))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
        // Cover and Back Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = group.coverImageUrl.ifBlank { "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1500&q=80" },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Top Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Row {
                        IconButton(
                            onClick = { showShareModal = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                        }
                        if (isOwner) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showSettingsModal = true },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Group Identity Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = group.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${group.name}" },
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color(0xFF1877F2), RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.name,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${when (group.category) { "recruitment" -> "Careers & Hiring"; "buy_sell" -> "Buy & Sell"; else -> "General" }} • ${if (group.isPrivate) "Private" else "Public"} • ${group.membersCount} members",
                            color = catColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Join/Joined, Invite, Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isMember) {
                        Button(
                            onClick = onLeaveGroup,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF45BD62), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Joined", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showInviteModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Invite", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onJoinGroup,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Join Group", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Row: Discussion, Events, Members, About
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    GroupDetailTab.values().forEach { tab ->
                        val selected = selectedTab == tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) Color(0xFF1877F2) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF1877F2))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            GroupDetailTab.DISCUSSION -> {
                if (canPost) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF162137))
                                        .clickable {
                                            autoLaunchPhotoPicker = false
                                            showCreatePostModal = true
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = currentUser.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${currentUser.name}" },
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = when {
                                            group.category == "buy_sell" -> "Sell something in ${group.name}..."
                                            isCareerCategory(group.category, group.name) -> "Post a job opportunity in ${group.name}..."
                                            else -> "Write something to ${group.name}..."
                                        },
                                        color = Color(0xFF94A3B8),
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFF1E293B))
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Photo / Video action
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                autoLaunchPhotoPicker = true
                                                showCreatePostModal = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Photo/Video", tint = Color(0xFF45BD62), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Photo/Video", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // Event action
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedTab = GroupDetailTab.EVENTS
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Event, contentDescription = "Event", tint = Color(0xFF1877F2), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Event", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    if (group.category == "buy_sell") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    autoLaunchPhotoPicker = false
                                                    showCreatePostModal = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Storefront, contentDescription = "Sell Item", tint = Color(0xFFF7B928), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sell Item", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else if (isCareerCategory(group.category, group.name)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    autoLaunchPhotoPicker = false
                                                    showCreatePostModal = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Work, contentDescription = "Post Job", tint = Color(0xFF45BD62), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Post Job", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(allPosts) { post ->
                    GroupPostCard(
                        post = post,
                        group = group,
                        currentUser = currentUser,
                        onLike = { onToggleLikePost(post.id, "like") },
                        onReact = { reaction -> onToggleLikePost(post.id, reaction) },
                        onComment = {
                            activeDiscussionPost = post.toPost(group)
                        },
                        onShare = {
                            activeSharePost = post.toPost(group)
                        },
                        onSave = {
                            Toast.makeText(context, "Post saved to your bookmarks", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = if (isOwner || post.author.id == currentUser.id) { { onDeletePost(post.id) } } else null,
                        onProfileClick = onProfileClick,
                        onOpenGallery = { imgs, idx ->
                            activePostForGallery = post.toPost(group)
                            activeGalleryState = Pair(imgs, idx)
                        },
                        onOpenReactions = {
                            activeReactionsPost = post.toPost(group)
                        }
                    )
                }

                if (allPosts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPostsLoading) {
                                CircularProgressIndicator(color = Color(0xFF1877F2))
                            } else {
                                Text("No discussions yet. Be the first to start a conversation!", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            GroupDetailTab.EVENTS -> {
                items(allEvents) { event ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(event.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(event.date, color = Color(0xFF1877F2), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(event.description, color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }

                if (allEvents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No group events scheduled.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }
            }

            GroupDetailTab.MEMBERS -> {
                if (groupMembersInfo.isNotEmpty()) {
                    items(groupMembersInfo) { member ->
                        val userIsAdmin = member.role.equals("admin", ignoreCase = true) || member.userId == group.adminId
                        val userIsMod = member.role.equals("moderator", ignoreCase = true)
                        var showActionMenu by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .clickable { onProfileClick(member.userId) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = member.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${member.name}" },
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(member.name.ifBlank { "Member" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (member.isVerified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        VerifiedBadge(size = 14.dp)
                                    }
                                    if (userIsAdmin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Admin", color = Color(0xFF1877F2), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    } else if (userIsMod) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Moderator", color = Color(0xFF45BD62), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text("@${member.username}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                if (member.postingDisabled) {
                                    Text("Posting muted", color = Color(0xFFF3425F), fontSize = 11.sp)
                                }
                            }

                            if (isOwner && member.userId != currentUser.id) {
                                Box {
                                    IconButton(onClick = { showActionMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Manage", tint = Color(0xFF94A3B8))
                                    }
                                    DropdownMenu(
                                        expanded = showActionMenu,
                                        onDismissRequest = { showActionMenu = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (member.postingDisabled) "Enable Posting" else "Mute Posting",
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                onToggleMemberPosting(member.userId, !member.postingDisabled)
                                                showActionMenu = false
                                            }
                                        )
                                        if (userIsAdmin) {
                                            DropdownMenuItem(
                                                text = { Text("Demote from Admin", color = Color.White) },
                                                onClick = {
                                                    onSetMemberRole(member.userId, "remove-admin")
                                                    showActionMenu = false
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Promote to Admin", color = Color.White) },
                                                onClick = {
                                                    onSetMemberRole(member.userId, "make-admin")
                                                    showActionMenu = false
                                                }
                                            )
                                        }
                                        if (!userIsMod) {
                                            DropdownMenuItem(
                                                text = { Text("Promote to Moderator", color = Color.White) },
                                                onClick = {
                                                    onSetMemberRole(member.userId, "make-moderator")
                                                    showActionMenu = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Remove from Group", color = Color(0xFFF3425F)) },
                                            onClick = {
                                                onRemoveMember(member.userId)
                                                showActionMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val memberIds = (group.members + group.adminId).distinct()
                    val groupUsers = allUsers.filter { memberIds.contains(it.id) }

                    items(groupUsers) { user ->
                        val userIsOwner = user.id == group.adminId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .clickable { onProfileClick(user.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = user.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (userIsOwner) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Admin (Owner)", color = Color(0xFF1877F2), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text("@${user.username}", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            if (isOwner && !userIsOwner) {
                                IconButton(onClick = { onRemoveMember(user.id) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = Color(0xFFF3425F))
                                }
                            }
                        }
                    }
                }
            }

            GroupDetailTab.ABOUT -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("About this community", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(group.description.ifBlank { "No description provided." }, color = Color(0xFFCBD5E1), fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (group.isPrivate) Icons.Default.Lock else Icons.Default.Public, contentDescription = null, tint = Color(0xFF94A3B8))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(if (group.isPrivate) "Private Group" else "Public Group", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(if (group.isPrivate) "Only members can see posts and members" else "Anyone can see who's in the group and what they post", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF94A3B8))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Created in 2026", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Active East Africa Community", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uploadState != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            PostUploadBottomPill(
                uploadState = uploadState,
                onDismiss = onDismissUpload
            )
        }
    }
}

    // Create Post Modal in Group
    if (showCreatePostModal) {
        CreateGroupPostDialog(
            group = group,
            currentUser = currentUser,
            autoPickPhotos = autoLaunchPhotoPicker,
            onDismiss = {
                showCreatePostModal = false
                autoLaunchPhotoPicker = false
            },
            onSubmit = { content, images, price, curr, cond, loc, stat, job, comp, jType, sal, appType, appVal ->
                onPost(content, images, price, curr, cond, loc, stat, job, comp, jType, sal, appType, appVal)
                showCreatePostModal = false
                autoLaunchPhotoPicker = false
            }
        )
    }

    // Invite Modal
    if (showInviteModal) {
        InviteFollowersDialog(
            group = group,
            followers = allUsers.filter { it.id != currentUser.id && !group.members.contains(it.id) },
            onDismiss = { showInviteModal = false },
            onInvite = { userId ->
                onInviteUser(userId)
                Toast.makeText(context, "Invitation sent!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Share Group Dialog
    if (showShareModal) {
        ShareGroupDialog(
            group = group,
            onDismiss = { showShareModal = false },
            onCopyLink = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Group Link", "https://unera.social/groups/${group.id}")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Group link copied to clipboard!", Toast.LENGTH_SHORT).show()
                showShareModal = false
            }
        )
    }

    // Settings Modal
    if (showSettingsModal) {
        GroupSettingsDialog(
            group = group,
            onDismiss = { showSettingsModal = false },
            onSave = { name, desc, postingAllowed ->
                onUpdateSettings(name, desc, postingAllowed)
                showSettingsModal = false
            },
            onDelete = {
                showSettingsModal = false
                onDeleteGroup()
            }
        )
    }

    // Discussions Modal (Full Comments Sheet matching web)
    activeDiscussionPost?.let { curPost ->
        DiscussionsSheetDialog(
            post = curPost,
            currentUser = currentUser,
            onDismiss = { activeDiscussionPost = null },
            onAddComment = { commentText ->
                onAddPostComment(curPost.id, commentText)
            },
            onLikeComment = { _ -> },
            onProfileClick = { user ->
                activeDiscussionPost = null
                onProfileClick(user.id)
            },
            onLikePost = {
                onToggleLikePost(curPost.id, "like")
            },
            onReactPost = { rType ->
                onToggleLikePost(curPost.id, rType)
            },
            onSharePost = {
                activeDiscussionPost = null
                activeSharePost = curPost
            },
            onSavePost = { },
            onOpenGallery = { imgs, idx ->
                activePostForGallery = curPost
                activeGalleryState = Pair(imgs, idx)
            }
        )
    }

    // Reactions Sheet Modal
    activeReactionsPost?.let { curPost ->
        ReactionsSheetDialog(
            post = curPost,
            allUsers = allUsers,
            onDismiss = { activeReactionsPost = null },
            onUserClick = { user ->
                activeReactionsPost = null
                onProfileClick(user.id)
            }
        )
    }

    // Share Modal
    activeSharePost?.let { curPost ->
        ShareBottomSheetDialog(
            post = curPost,
            onDismiss = { activeSharePost = null },
            onShareToFeed = {
                onSharePost(curPost.id)
                Toast.makeText(context, "Shared to feed!", Toast.LENGTH_SHORT).show()
                activeSharePost = null
            },
            onShareToGroup = {
                onSharePost(curPost.id)
                Toast.makeText(context, "Shared to group!", Toast.LENGTH_SHORT).show()
                activeSharePost = null
            },
            onSendMessage = {
                Toast.makeText(context, "Share link ready to send", Toast.LENGTH_SHORT).show()
                activeSharePost = null
            },
            onCopyLink = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Post Link", "https://unera.social/posts/${curPost.id}")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Post link copied to clipboard!", Toast.LENGTH_SHORT).show()
                activeSharePost = null
            }
        )
    }

    // Fullscreen Gallery Modal
    activeGalleryState?.let { (imgs, idx) ->
        activePostForGallery?.let { curPost ->
            GalleryViewerDialog(
                images = imgs,
                initialIndex = idx,
                post = curPost,
                onDismiss = {
                    activeGalleryState = null
                    activePostForGallery = null
                },
                onLike = { onToggleLikePost(curPost.id, "like") },
                onComment = {
                    activeGalleryState = null
                    activeDiscussionPost = curPost
                },
                onShare = {
                    activeGalleryState = null
                    activeSharePost = curPost
                },
                onSave = { }
            )
        }
    }
}

@Composable
fun GroupMediaGrid(
    images: List<String>,
    onImageClick: (Int) -> Unit
) {
    when (images.size) {
        1 -> {
            AsyncImage(
                model = images[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImageClick(0) }
            )
        }
        2 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AsyncImage(
                    model = images[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .clickable { onImageClick(0) }
                )
                AsyncImage(
                    model = images[1],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                        .clickable { onImageClick(1) }
                )
            }
        }
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AsyncImage(
                    model = images[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .clickable { onImageClick(0) }
                )
                Column(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AsyncImage(
                        model = images[1],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topEnd = 10.dp))
                            .clickable { onImageClick(1) }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomEnd = 10.dp))
                            .clickable { onImageClick(2) }
                    ) {
                        AsyncImage(
                            model = images[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (images.size > 3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${images.size - 2}",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupPostCard(
    post: GroupPost,
    group: Group,
    currentUser: User,
    onLike: () -> Unit,
    onReact: (String) -> Unit = {},
    onComment: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    onProfileClick: (Long) -> Unit,
    onOpenGallery: (List<String>, Int) -> Unit = { _, _ -> },
    onOpenReactions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showReactionDock by remember { mutableStateOf(false) }
    var isExpandedText by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isCareer = group.category == "recruitment" ||
        isCareerCategory(group.category, group.name) ||
        !post.jobTitle.isNullOrBlank()
    val isBuySell = group.category == "buy_sell" || post.price != null

    val hasApplicationMethod = !post.applicationValue.isNullOrBlank() &&
        (post.applicationType == "email" || post.applicationType == "link" ||
         post.applicationValue.contains("@") || post.applicationValue.startsWith("http"))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Author Avatar, Name, Verified Badge, Category, More Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = post.author.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${post.author.name}" },
                    contentDescription = post.author.name,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFF1E293B), CircleShape)
                        .clickable { onProfileClick(post.author.id) },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onProfileClick(post.author.id) }
                    ) {
                        Text(
                            text = post.author.name,
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (post.author.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge(size = 15.dp)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(post.createdAt, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text("•", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        if (isCareer) {
                            Icon(Icons.Default.Work, contentDescription = null, tint = Color(0xFF45BD62), modifier = Modifier.size(12.dp))
                            Text("Recruitment", color = Color(0xFF45BD62), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else if (isBuySell) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFFF7B928), modifier = Modifier.size(12.dp))
                            Text("Marketplace", color = Color(0xFFF7B928), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color(0xFF1877F2), modifier = Modifier.size(12.dp))
                            Text("Group Post", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }

                // 3-dots more menu
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color(0xFF94A3B8))
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = Color(0xFFF3425F)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF3425F)) },
                                onClick = {
                                    showMoreMenu = false
                                    onDelete()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Report Post", color = Color(0xFFF8FAFC)) },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFF94A3B8)) },
                            onClick = {
                                showMoreMenu = false
                                Toast.makeText(context, "Post reported to group admins", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Category Feature: Careers / Recruitment (Job features & Apply button)
            if (isCareer) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    // JOB POSTING badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF45BD62).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF45BD62).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Work, contentDescription = null, tint = Color(0xFF45BD62), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("JOB POSTING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF45BD62))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Job Details Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162137)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = post.jobTitle ?: "Job Opening",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            if (!post.company.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF45BD62), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(post.company, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!post.location.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF45BD62), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(post.location, fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                                if (!post.jobType.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFF7B928), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(post.jobType, fontSize = 12.sp, color = Color(0xFFF7B928), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            if (!post.salary.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = Color(0xFF45BD62), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(post.salary, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF45BD62))
                                }
                            }

                            // APPLY BUTTON: Only rendered if user attached an application method!
                            if (hasApplicationMethod) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val appVal = post.applicationValue?.trim() ?: ""
                                            val isEmail = post.applicationType == "email" || appVal.contains("@")
                                            if (isEmail) {
                                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$appVal")).apply {
                                                    putExtra(Intent.EXTRA_SUBJECT, "Application for ${post.jobTitle ?: "Position"} at ${post.company ?: group.name}")
                                                }
                                                context.startActivity(intent)
                                            } else {
                                                val url = if (!appVal.startsWith("http://") && !appVal.startsWith("https://")) "https://$appVal" else appVal
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            }
                                            Toast.makeText(context, "Opening application...", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open application: ${post.applicationValue}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Category Feature: Buy & Sell (Price tag, condition, location)
            if (isBuySell) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${post.currency} ${post.price ?: ""}",
                            color = Color(0xFF45BD62),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        if (!post.condition.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF7B928).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(post.condition, color = Color(0xFFF7B928), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (!post.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF7B928), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(post.location, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Text Content (with See More / See Less)
            if (post.content.isNotBlank()) {
                val words = remember(post.content) { post.content.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() } }
                val isLong = words.size > 25 || post.content.length > 180
                val displayText = if (isExpandedText || !isLong) post.content else words.take(25).joinToString(" ") + "…"

                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        text = displayText,
                        color = Color(0xFFF8FAFC),
                        fontSize = 16.sp,
                        lineHeight = 23.sp
                    )
                    if (isLong) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isExpandedText) "See Less" else "See More",
                            color = Color(0xFF1877F2),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable { isExpandedText = !isExpandedText }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Attached Media Grid
            if (post.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                GroupMediaGrid(
                    images = post.imageUrls,
                    onImageClick = { idx -> onOpenGallery(post.imageUrls, idx) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reactions / Discussions / Shares Summary Header
            if (post.likesCount > 0 || post.commentsCount > 0 || post.sharesCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (post.likesCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onOpenReactions)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                Text("👍", fontSize = 14.sp)
                                Text("❤️", fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${post.likesCount} Reactions",
                                color = Color(0xFFF8FAFC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (post.commentsCount > 0) {
                            Text(
                                text = "${post.commentsCount} Discussions",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(onClick = onComment)
                            )
                        }
                        if (post.commentsCount > 0 && post.sharesCount > 0) {
                            Text("•", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                        if (post.sharesCount > 0) {
                            Text(
                                text = "${post.sharesCount} Shares",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(onClick = onShare)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            // Reactions Dock on Long Press (AnimatedVisibility)
            AnimatedVisibility(
                visible = showReactionDock,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0B1120))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    REACTION_EMOJIS.take(7).forEach { (type, emoji) ->
                        Text(
                            text = emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onReact(type)
                                    showReactionDock = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }

            // Action Buttons Row: React, Discuss, Share, Save (Matching Feed & Web)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // React Button (Tap = like/unlike, Long-press = show dock)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(post.id, showReactionDock) {
                                detectTapGestures(
                                    onLongPress = { showReactionDock = true },
                                    onTap = {
                                        if (showReactionDock) showReactionDock = false
                                        else onLike()
                                    }
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val reactionEmoji = post.myReaction?.let { getReactionEmoji(it) }
                            if (reactionEmoji != null) {
                                Text(text = reactionEmoji, fontSize = 20.sp)
                            } else {
                                Icon(
                                    imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (post.isLiked) Color(0xFFF43F5E) else Color(0xFFF8FAFC),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (post.likesCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatCount(post.likesCount),
                                    color = if (post.isLiked || post.myReaction != null) Color(0xFFF43F5E) else Color(0xFFF8FAFC),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Discuss Button
                    DiscussButton(
                        commentCount = post.commentsCount,
                        onClick = onComment
                    )

                    // Share Button (Facing Northeast: ↗)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShare)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Share",
                            tint = Color(0xFFF8FAFC),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer(rotationZ = -35f)
                        )
                        if (post.sharesCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatCount(post.sharesCount),
                                color = Color(0xFFF8FAFC),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Save Post Button
                IconButton(onClick = onSave) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Comment Preview (Latest comment)
            if (post.comments.isNotEmpty()) {
                val latestComment = post.comments.last()
                HorizontalDivider(color = Color(0xFF1E293B).copy(alpha = 0.5f), thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onComment)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = latestComment.author.profileImageUrl.ifBlank { "https://ui-avatars.com/api/?name=${latestComment.author.name}" },
                        contentDescription = latestComment.author.name,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF162137))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(latestComment.author.name, color = Color(0xFFF8FAFC), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(latestComment.text, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(latestComment.timestamp, color = Color(0xFF64748B), fontSize = 11.sp)
                            Text("Reply", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onComment))
                        }
                    }
                }
            }
        }
    }
}

// Dialogs

@Composable
fun CategorySelectionDialog(
    onDismiss: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf(
        Triple("general", "General", "Standard community for discussions, sharing and connection"),
        Triple("recruitment", "Careers & Recruitment", "Find talent, professional networking and job openings"),
        Triple("buy_sell", "Buy and Sell", "Marketplace for trading goods, electronics and items")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Group Category", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { (catId, title, desc) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCategory(catId) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(desc, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        },
        containerColor = Color(0xFF0F172A)
    )
}

@Composable
fun CreateGroupDialog(
    category: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, privacy: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Community", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1877F2)
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF1877F2)
                    ),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Private Group", color = Color.White, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), description.trim(), if (isPrivate) "private" else "public")
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        },
        containerColor = Color(0xFF0F172A)
    )
}

@Composable
fun CreateGroupPostDialog(
    group: Group,
    currentUser: User,
    autoPickPhotos: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (String, List<String>, Double?, String, String?, String?, String, String?, String?, String?, String?, String?, String?) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Photo picker launcher (Multiple images, max 10, photos only)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedPhotos = (selectedPhotos + uris).distinct().take(10)
        }
    }

    // Auto-launch picker if triggered from Photo/Video action
    LaunchedEffect(autoPickPhotos) {
        if (autoPickPhotos) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    // Buy & Sell
    var priceStr by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("Used - Good") }
    var location by remember { mutableStateOf("") }

    // Recruitment
    val isCareer = group.category == "recruitment" || isCareerCategory(group.category, group.name)
    var jobTitle by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var jobLocation by remember { mutableStateOf("") }
    var jobType by remember { mutableStateOf("Full-time") }
    var applicationType by remember { mutableStateOf<String?>(null) } // "email" or "link"
    var applicationValue by remember { mutableStateOf("") }

    val canPublish = content.isNotBlank() || selectedPhotos.isNotEmpty() || (isCareer && jobTitle.isNotBlank()) || (group.category == "buy_sell" && priceStr.isNotBlank())

    val modalTitle = when {
        group.category == "buy_sell" -> "Sell an Item"
        isCareer -> "Post a Job Opening"
        else -> "Create Group Post"
    }

    val submitButtonText = when {
        group.category == "buy_sell" -> "List Item in Group"
        isCareer -> "Post Job"
        else -> "Post to Community"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FeatheredAvatar(
                                name = currentUser.name,
                                imageUrl = currentUser.profileImageUrl,
                                size = 42.dp,
                                username = currentUser.username
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = modalTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFF8FAFC)
                                )
                                Text(
                                    text = "Posting to ${group.name}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1877F2),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable form fields
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Category specific extra fields: Buy & Sell
                        if (group.category == "buy_sell") {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = priceStr,
                                        onValueChange = { priceStr = it },
                                        label = { Text("Price (USD)", color = Color(0xFF94A3B8)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF0B1120),
                                            unfocusedContainerColor = Color(0xFF0B1120),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = condition,
                                        onValueChange = { condition = it },
                                        label = { Text("Condition (e.g. Brand New, Used - Like New)", color = Color(0xFF94A3B8)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF0B1120),
                                            unfocusedContainerColor = Color(0xFF0B1120),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = location,
                                        onValueChange = { location = it },
                                        label = { Text("Pickup Location / City", color = Color(0xFF94A3B8)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF0B1120),
                                            unfocusedContainerColor = Color(0xFF0B1120),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Category specific extra fields: Recruitment / Careers
                        if (isCareer) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = jobTitle,
                                        onValueChange = { jobTitle = it },
                                        label = { Text("Job Title", color = Color(0xFF94A3B8)) },
                                        placeholder = { Text("e.g. Senior Frontend Engineer", color = Color(0xFF64748B)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF0B1120),
                                            unfocusedContainerColor = Color(0xFF0B1120),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = company,
                                        onValueChange = { company = it },
                                        label = { Text("Company Name", color = Color(0xFF94A3B8)) },
                                        placeholder = { Text("Company name", color = Color(0xFF64748B)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF0B1120),
                                            unfocusedContainerColor = Color(0xFF0B1120),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = jobType,
                                            onValueChange = { jobType = it },
                                            label = { Text("Job Type", color = Color(0xFF94A3B8)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF0B1120),
                                                unfocusedContainerColor = Color(0xFF0B1120),
                                                focusedTextColor = Color(0xFFF8FAFC),
                                                unfocusedTextColor = Color(0xFFF8FAFC),
                                                focusedBorderColor = Color(0xFF1877F2),
                                                unfocusedBorderColor = Color(0xFF1E293B)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )

                                        OutlinedTextField(
                                            value = salary,
                                            onValueChange = { salary = it },
                                            label = { Text("Salary Range", color = Color(0xFF94A3B8)) },
                                            placeholder = { Text("e.g. $70k - $90k", color = Color(0xFF64748B)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF0B1120),
                                                unfocusedContainerColor = Color(0xFF0B1120),
                                                focusedTextColor = Color(0xFFF8FAFC),
                                                unfocusedTextColor = Color(0xFFF8FAFC),
                                                focusedBorderColor = Color(0xFF1877F2),
                                                unfocusedBorderColor = Color(0xFF1E293B)
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = jobLocation,
                                        onValueChange = { jobLocation = it },
                                        label = { Text("Job Location", color = Color(0xFF94A3B8)) },
                                        placeholder = { Text("e.g. San Francisco, CA / Remote", color = Color(0xFF64748B)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF0B1120),
                                            unfocusedContainerColor = Color(0xFF0B1120),
                                            focusedTextColor = Color(0xFFF8FAFC),
                                            unfocusedTextColor = Color(0xFFF8FAFC),
                                            focusedBorderColor = Color(0xFF1877F2),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Application Method: Email or Link
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162137)),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "How can candidates apply?",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFF8FAFC)
                                            )
                                            Text(
                                                text = "Optional — if left empty, Apply button is hidden",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { applicationType = "email" },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (applicationType == "email") Color(0xFF1877F2).copy(alpha = 0.25f) else Color(0xFF0B1120),
                                                        contentColor = if (applicationType == "email") Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                                    ),
                                                    border = BorderStroke(1.dp, if (applicationType == "email") Color(0xFF38BDF8) else Color(0xFF1E293B)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("By Email", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }

                                                Button(
                                                    onClick = { applicationType = "link" },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (applicationType == "link") Color(0xFF1877F2).copy(alpha = 0.25f) else Color(0xFF0B1120),
                                                        contentColor = if (applicationType == "link") Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                                    ),
                                                    border = BorderStroke(1.dp, if (applicationType == "link") Color(0xFF38BDF8) else Color(0xFF1E293B)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("By External Link", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }

                                            if (applicationType == "email") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = applicationValue,
                                                    onValueChange = { applicationValue = it },
                                                    label = { Text("Application Email Address", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                                    placeholder = { Text("e.g. jobs@company.com", color = Color(0xFF64748B)) },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color(0xFF0B1120),
                                                        unfocusedContainerColor = Color(0xFF0B1120),
                                                        focusedTextColor = Color(0xFFF8FAFC),
                                                        unfocusedTextColor = Color(0xFFF8FAFC),
                                                        focusedBorderColor = Color(0xFF1877F2),
                                                        unfocusedBorderColor = Color(0xFF1E293B)
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            if (applicationType == "link") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = applicationValue,
                                                    onValueChange = { applicationValue = it },
                                                    label = { Text("Application Website / Careers URL", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                                    placeholder = { Text("e.g. https://company.com/careers/apply", color = Color(0xFF64748B)) },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color(0xFF0B1120),
                                                        unfocusedContainerColor = Color(0xFF0B1120),
                                                        focusedTextColor = Color(0xFFF8FAFC),
                                                        unfocusedTextColor = Color(0xFFF8FAFC),
                                                        focusedBorderColor = Color(0xFF1877F2),
                                                        unfocusedBorderColor = Color(0xFF1E293B)
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            if (applicationType != null) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Remove application method (hide Apply button)",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF94A3B8),
                                                    modifier = Modifier
                                                        .align(Alignment.End)
                                                        .clickable {
                                                            applicationType = null
                                                            applicationValue = ""
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Content text input
                        item {
                            OutlinedTextField(
                                value = content,
                                onValueChange = { content = it },
                                placeholder = {
                                    Text(
                                        text = when {
                                            group.category == "buy_sell" -> "Describe the item you are selling..."
                                            isCareer -> "Describe the role, responsibilities, requirements, qualifications, and how to apply..."
                                            else -> "What's on your mind? Share thoughts, ask questions, or contribute to ${group.name}..."
                                        },
                                        color = Color(0xFF64748B),
                                        fontSize = 15.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0B1120),
                                    unfocusedContainerColor = Color(0xFF0B1120),
                                    focusedTextColor = Color(0xFFF8FAFC),
                                    unfocusedTextColor = Color(0xFFF8FAFC),
                                    focusedBorderColor = Color(0xFF1877F2),
                                    unfocusedBorderColor = Color(0xFF1E293B)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 220.dp)
                            )
                        }

                        // Selected photos preview row
                        if (selectedPhotos.isNotEmpty()) {
                            item {
                                Column {
                                    Text(
                                        text = "${selectedPhotos.size} photo${if (selectedPhotos.size > 1) "s" else ""} attached",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(selectedPhotos) { uri ->
                                            Box(modifier = Modifier.size(80.dp)) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Selected photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                                )
                                                IconButton(
                                                    onClick = {
                                                        selectedPhotos = selectedPhotos.filter { it != uri }
                                                    },
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = 4.dp, y = (-4).dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEF4444))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove photo",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (selectedPhotos.size < 10) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF162137))
                                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            photoPickerLauncher.launch(
                                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "Add more",
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Photo Picker action button & note
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF162137)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Add Photos",
                                            tint = Color(0xFF45BD62),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (selectedPhotos.isEmpty()) "Add Photos to Post" else "Add More Photos",
                                                color = Color(0xFFF8FAFC),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Photos only (up to 10). Video uploads disabled in groups.",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                if (canPublish) {
                                    val photoUriStrings = selectedPhotos.map { it.toString() }
                                    val parsedPrice = priceStr.toDoubleOrNull()
                                    val finalAppType = if (applicationValue.isNotBlank()) (applicationType ?: if (applicationValue.contains("@")) "email" else "link") else null
                                    val finalAppVal = applicationValue.ifBlank { null }
                                    onSubmit(
                                        content.trim(),
                                        photoUriStrings,
                                        parsedPrice,
                                        "USD",
                                        condition.ifBlank { null },
                                        if (isCareer) jobLocation.ifBlank { null } else location.ifBlank { null },
                                        "available",
                                        jobTitle.ifBlank { null },
                                        company.ifBlank { null },
                                        jobType,
                                        salary.ifBlank { null },
                                        finalAppType,
                                        finalAppVal
                                    )
                                }
                            },
                            enabled = canPublish,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1877F2),
                                disabledContainerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp)
                        ) {
                            Text(
                                text = submitButtonText,
                                fontWeight = FontWeight.Bold,
                                color = if (canPublish) Color.White else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InviteFollowersDialog(
    group: Group,
    followers: List<User>,
    onDismiss: () -> Unit,
    onInvite: (Long) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val invitedIds = remember { mutableStateListOf<Long>() }

    val filtered = followers.filter {
        search.isBlank() || it.name.contains(search, ignoreCase = true) || it.username.contains(search, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Followers to ${group.name}", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search users...", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(filtered) { user ->
                        val alreadyInvited = invitedIds.contains(user.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = user.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(user.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("@${user.username}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    if (!alreadyInvited) {
                                        invitedIds.add(user.id)
                                        onInvite(user.id)
                                    }
                                },
                                enabled = !alreadyInvited,
                                colors = ButtonDefaults.buttonColors(containerColor = if (alreadyInvited) Color(0xFF1E293B) else Color(0xFF1877F2)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(if (alreadyInvited) "Invited" else "Invite", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Color(0xFF1877F2)) }
        },
        containerColor = Color(0xFF0F172A)
    )
}

@Composable
fun ShareGroupDialog(
    group: Group,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Community", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("https://unera.social/groups/${group.id}", color = Color(0xFF1877F2), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCopyLink,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Group Link")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF94A3B8)) }
        },
        containerColor = Color(0xFF0F172A)
    )
}

@Composable
fun GroupSettingsDialog(
    group: Group,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, postingAllowed: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var desc by remember { mutableStateOf(group.description) }
    var postingAllowed by remember { mutableStateOf(group.memberPostingAllowed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Member Posting", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("When off, only admin can post", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(
                        checked = postingAllowed,
                        onCheckedChange = { postingAllowed = it }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3425F).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Group", color = Color(0xFFF3425F), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), desc.trim(), postingAllowed) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        },
        containerColor = Color(0xFF0F172A)
    )
}
