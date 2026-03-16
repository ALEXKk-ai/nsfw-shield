package com.nsfwshield.app.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Welcome onboarding screen — first screen users see.
 * Shows app value proposition then leads into disclosure screens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(

    onSetupComplete: () -> Unit,
    onSkipToMain: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.Shield,
            iconColor = Color(0xFF1A73E8),
            title = "Welcome to NSFW Shield",
            subtitle = "Intelligent content filtering that protects you and your family from explicit content — across all apps and browsers.",
            accentColor = Color(0xFF1A73E8)
        ),
        OnboardingPage(
            icon = Icons.Default.PhoneAndroid,
            iconColor = Color(0xFF00BFA5),
            title = "100% On-Device",
            subtitle = "All AI detection happens on your device. Your images, browsing data, and screen content are never sent to any server. Ever.",
            accentColor = Color(0xFF00BFA5)
        ),
        OnboardingPage(
            icon = Icons.Default.Psychology,
            iconColor = Color(0xFF7C4DFF),
            title = "Smart Detection",
            subtitle = "AI-powered analysis reduces false positives. Medical, educational, and cooking content won't trigger unnecessary blocks.",
            accentColor = Color(0xFF7C4DFF)
        ),
        OnboardingPage(
            icon = Icons.Default.Tune,
            iconColor = Color(0xFFFFA726),
            title = "You're in Control",
            subtitle = "Customize sensitivity, choose which categories to block, and set up accountability partners for extra support.",
            accentColor = Color(0xFFFFA726)
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val currentPageData = pages[currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1419),
                        Color(0xFF16202A),
                        Color(0xFF0F1419)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) { pageIndex ->
                val page = pages[pageIndex]
                val pageScrollState = rememberScrollState()
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(pageScrollState)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated icon
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = page.iconColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(100.dp)
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = page.iconColor,
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = page.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                pages.indices.forEach { index ->
                    Surface(
                        shape = CircleShape,
                        color = if (index == currentPage) currentPageData.accentColor
                        else Color(0xFF37474F),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentPage) 10.dp else 8.dp)
                    ) {}
                }
            }

            // Action button
            if (currentPage < pages.lastIndex) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = currentPage + 1,
                                animationSpec = tween(400)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentPageData.accentColor
                    )
                ) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = onSetupComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentPageData.accentColor
                    )
                ) {
                    Text(
                        text = "Set Up Protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Skip option (first 3 pages only)
            if (currentPage < pages.lastIndex) {
                TextButton(
                    onClick = onSkipToMain,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Skip for now",
                        color = Color(0xFF78909C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // Spacer to keep layout consistent on the last page
                Spacer(modifier = Modifier.height(56.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val subtitle: String,
    val accentColor: Color
)
