package com.meta.wearable.retail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Industrial Hardening: Standardizing Style Extensions
// Replaced experimental Style API with stable Modifier extensions

@Composable
fun Modifier.userChatBubble(): Modifier =
    this
        .background(SpressoTheme.colors.primary, RoundedCornerShape(12.dp))

@Composable
fun Modifier.assistantChatBubble(): Modifier =
    this
        .background(SpressoTheme.colors.surface, RoundedCornerShape(12.dp))

@Composable
fun Modifier.productCard(): Modifier =
    this
        .background(SpressoTheme.colors.surface, RoundedCornerShape(8.dp))
