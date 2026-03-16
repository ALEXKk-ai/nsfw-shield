package com.nsfwshield.app.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nsfwshield.app.data.ActivityLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity Reports and Logs screen.
 * Shows blocked content events, category breakdowns, and filtering stats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Today", "This Week", "This Month")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(selectedTabIndex = uiState.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.setTimeRange(index) },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Summary stats
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReportStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Blocked",
                            value = uiState.blockedCount.toString(),
                            color = Color(0xFFEF5350)
                        )
                        ReportStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Overrides",
                            value = uiState.overrideCount.toString(),
                            color = Color(0xFFFFA726)
                        )
                        ReportStatCard(
                            modifier = Modifier.weight(1f),
                            label = "DNS Blocks",
                            value = uiState.dnsBlockCount.toString(),
                            color = Color(0xFF1A73E8)
                        )
                    }
                }

                // Category breakdown
                if (uiState.topCategories.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Top Categories",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    val totalBlocked = uiState.blockedCount.coerceAtLeast(1)
                    val categoryColors = listOf(
                        Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFF7C4DFF),
                        Color(0xFFFF7043), Color(0xFF26A69A)
                    )

                    items(uiState.topCategories) { categoryCount ->
                        val colorIndex = uiState.topCategories.indexOf(categoryCount) % categoryColors.size
                        CategoryBreakdownItem(
                            category = categoryCount.category,
                            count = categoryCount.count,
                            total = totalBlocked,
                            color = categoryColors[colorIndex]
                        )
                    }
                }

                // Recent events
                if (uiState.recentEvents.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Recent Activity",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    items(uiState.recentEvents) { event ->
                        RecentEventCard(event)
                    }
                }

                // Empty state
                if (!uiState.isLoading && uiState.blockedCount == 0 && uiState.recentEvents.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF66BB6A),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No activity yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Blocked content events will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ReportStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CategoryBreakdownItem(
    category: String,
    count: Int,
    total: Int,
    color: Color
) {
    val fraction = count.toFloat() / total.toFloat()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color,
                modifier = Modifier.size(12.dp)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                category,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "$count",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentEventCard(event: ActivityLogEntry) {
    val eventColor = when (event.eventType) {
        "BLOCKED" -> Color(0xFFEF5350)
        "DNS_BLOCK" -> Color(0xFF1A73E8)
        "OVERRIDE" -> Color(0xFF78909C)
        else -> Color(0xFF66BB6A)
    }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeAgo = getTimeAgo(event.timestamp)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = eventColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = when (event.eventType) {
                        "BLOCKED" -> Icons.Default.Block
                        "DNS_BLOCK" -> Icons.Default.Dns
                        "OVERRIDE" -> Icons.Default.LockOpen
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = eventColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "${event.category} ${event.actionTaken.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${event.source} • $timeAgo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "$days days ago"
    }
}
