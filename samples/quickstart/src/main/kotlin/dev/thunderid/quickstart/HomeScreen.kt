/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.quickstart

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.actions.SignOutButton
import dev.thunderid.compose.components.presentation.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Home screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen() {
    val thunder = LocalThunderID.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) logAccessToken(thunder)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
                    Triple("Saved", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
                    Triple("Trips", Icons.Filled.CardTravel, Icons.Outlined.CardTravel),
                    Triple("Inbox", Icons.Filled.Email, Icons.Outlined.Email),
                    Triple("Profile", Icons.Filled.Person, Icons.Outlined.Person),
                ).forEachIndexed { index, (label, filled, outlined) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(if (selectedTab == index) filled else outlined, contentDescription = label)
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ExploreTab(onProfileTap = { selectedTab = 4 })
                1 -> PlaceholderTab("Saved", Icons.Outlined.FavoriteBorder)
                2 -> PlaceholderTab("Trips", Icons.Outlined.CardTravel)
                3 -> PlaceholderTab("Inbox", Icons.Outlined.Email)
                4 -> ProfileTab()
            }
        }
    }
}

private suspend fun logAccessToken(thunder: dev.thunderid.compose.ThunderIDState) {
    try {
        val token = thunder.client.getAccessToken()
        android.util.Log.d("HomeScreen", "access token: $token")
        android.util.Log.d("HomeScreen", "token payload: ${decodeJwtPayload(token)}")
    } catch (e: Exception) {
        android.util.Log.d("HomeScreen", "could not get access token: $e")
    }
}

private fun decodeJwtPayload(token: String): String {
    val parts = token.split(".")
    if (parts.size != 3) return "(not a JWT)"
    return try {
        val padded = parts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
        String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE))
    } catch (_: Exception) {
        "(decode error)"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Explore tab
// ─────────────────────────────────────────────────────────────────────────────

private data class Listing(
    val title: String,
    val location: String,
    val price: Int,
    val rating: Double,
    val imageUrl: String,
)

private val listings = listOf(
    Listing("Cozy Mountain Retreat", "Aspen, Colorado", 189, 4.92, "https://picsum.photos/seed/acme1/400/280"),
    Listing("Beachfront Villa", "Malibu, California", 342, 4.87, "https://picsum.photos/seed/acme2/400/280"),
    Listing("City Centre Loft", "New York, NY", 215, 4.78, "https://picsum.photos/seed/acme3/400/280"),
    Listing("Lakeside Cabin", "Lake Tahoe, Nevada", 156, 4.95, "https://picsum.photos/seed/acme4/400/280"),
)

private val categories = listOf("Stays", "Experiences", "Adventures", "Luxe")
private val categoryIcons = listOf(
    Icons.Outlined.Home,
    Icons.Outlined.Map,
    Icons.Outlined.Terrain,
    Icons.Outlined.Diamond,
)
private val sorts = listOf("Popular", "Near", "Best Price")

@Composable
private fun ExploreTab(onProfileTap: () -> Unit) {
    val thunder = LocalThunderID.current
    val cs = MaterialTheme.colorScheme
    var categoryIndex by remember { mutableIntStateOf(0) }
    var sortIndex by remember { mutableIntStateOf(0) }

    val firstName = remember(thunder.user) {
        (thunder.user?.claims?.get("given_name") as? String)?.takeIf { it.isNotEmpty() }
            ?: thunder.user?.displayName?.split(" ")?.firstOrNull()
            ?: "there"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = cs.primary)
            Spacer(Modifier.width(6.dp))
            Text("ACME Booking", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            UserAvatar(user = thunder.user, radius = 18.dp, modifier = Modifier.clickable { onProfileTap() })
            Spacer(Modifier.width(8.dp))
        }

        // Welcome heading
        Text(
            text = "Where Would you\nLike to Stay, $firstName?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )

        // Search bar
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .background(cs.surfaceContainerHighest, CircleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("Search destinations...", color = cs.onSurfaceVariant)
        }

        // Category chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            categories.forEachIndexed { i, label ->
                val selected = i == categoryIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { categoryIndex = i },
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                if (selected) cs.primary else cs.surfaceContainerHighest,
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcons[i],
                            contentDescription = label,
                            tint = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) cs.primary else cs.onSurfaceVariant,
                    )
                }
            }
        }

        // Sort tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            sorts.forEachIndexed { i, label ->
                val selected = i == sortIndex
                Column(
                    modifier = Modifier
                        .clickable { sortIndex = i }
                        .padding(end = if (i < sorts.size - 1) 20.dp else 0.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) cs.onSurface else cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(cs.primary, RoundedCornerShape(1.dp)),
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("See More", color = cs.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        // Listings grid (2 columns)
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listings.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { listing ->
                        ListingCard(listing = listing, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile tab
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab() {
    val thunder = LocalThunderID.current
    val cs = MaterialTheme.colorScheme
    var showEditProfile by remember { mutableStateOf(false) }

    val displayName = remember(thunder.user) {
        val given = thunder.user?.claims?.get("given_name") as? String ?: ""
        val family = thunder.user?.claims?.get("family_name") as? String ?: ""
        val full = listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
        full.ifEmpty { thunder.user?.username ?: "Guest" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Profile",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
            }
        }

        // Profile card
        OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    UserAvatar(user = thunder.user, radius = 40.dp)
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .background(cs.surface, CircleShape),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Los Angeles, CA", color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Stats card
        OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem("24", "Trips")
                VerticalDivider(modifier = Modifier.height(44.dp))
                StatItem("22", "Reviews")
                VerticalDivider(modifier = Modifier.height(44.dp))
                StatItem("2", "Years on ACME")
            }
        }
        Spacer(Modifier.height(12.dp))

        // Feature cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard(
                label = "Past trips",
                imageUrl = "https://picsum.photos/seed/trips/300/200",
                isNew = true,
                modifier = Modifier.weight(1f),
            )
            FeatureCard(
                label = "Connections",
                imageUrl = "https://picsum.photos/seed/connect/300/200",
                isNew = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))

        // Edit Profile button
        OutlinedButton(
            onClick = { showEditProfile = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Edit Profile")
        }
        Spacer(Modifier.height(12.dp))

        // Sign Out button (SDK component)
        SignOutButton(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }

    if (showEditProfile) {
        ModalBottomSheet(onDismissRequest = { showEditProfile = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                UserProfile(
                    modifier = Modifier.fillMaxWidth(),
                    onSaved = { showEditProfile = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Placeholder tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaceholderTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UserAvatar(user: User?, radius: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val pictureUrl = remember(user) {
        user?.profilePicture?.takeIf { it.isNotEmpty() }
            ?: (user?.claims?.get("picture") as? String)?.takeIf { it.isNotEmpty() }
    }
    var bitmap by remember(pictureUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pictureUrl) {
        if (pictureUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    bitmap = BitmapFactory.decodeStream(java.net.URL(pictureUrl).openStream())?.asImageBitmap()
                } catch (_: Exception) {}
            }
        }
    }

    val size = radius * 2
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        val initial = user?.displayName?.firstOrNull()?.uppercaseChar()?.toString()
            ?: user?.email?.firstOrNull()?.uppercaseChar()?.toString()
            ?: "?"
        Box(
            modifier = modifier.size(size).background(cs.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                color = cs.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = (radius.value * 0.9).sp,
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.defaultMinSize(minWidth = 72.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeatureCard(label: String, imageUrl: String, isNew: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                bitmap = BitmapFactory.decodeStream(java.net.URL(imageUrl).openStream())?.asImageBitmap()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(cs.surfaceContainerHighest))
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (isNew) {
                Text(
                    "NEW",
                    color = cs.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(cs.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                Spacer(Modifier.height(0.dp))
            }
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ListingCard(listing: Listing, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var bitmap by remember(listing.imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(listing.imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                bitmap = BitmapFactory.decodeStream(java.net.URL(listing.imageUrl).openStream())?.asImageBitmap()
            } catch (_: Exception) {}
        }
    }

    androidx.compose.material3.Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(cs.surfaceContainerHighest))
                }
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listing.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.Star, contentDescription = null, tint = cs.primary, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(String.format("%.2f", listing.rating), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    listing.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text("\$${listing.price}/night", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
