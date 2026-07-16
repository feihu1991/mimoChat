package com.example.mimochat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun LoadingIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    alternateImageVector: ImageVector? = null
) {
    var showAlternate by remember(imageVector, alternateImageVector) { mutableStateOf(false) }
    LaunchedEffect(imageVector, alternateImageVector) {
        while (isActive && alternateImageVector != null) {
            delay(720)
            showAlternate = !showAlternate
        }
    }
    AnimatedContent(
        targetState = showAlternate && alternateImageVector != null,
        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
        label = "functional-status-icon"
    ) { alternate ->
        Icon(
            imageVector = if (alternate) alternateImageVector!! else imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}
