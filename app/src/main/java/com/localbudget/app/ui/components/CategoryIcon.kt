package com.localbudget.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.localbudget.app.ui.theme.parseColorHex

fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "Restaurant" -> Icons.Default.Restaurant
        "ShoppingCart" -> Icons.Default.ShoppingCart
        "DirectionsSubway" -> Icons.Default.DirectionsSubway
        "Home" -> Icons.Default.Home
        "Bolt" -> Icons.Default.Bolt
        "Wifi" -> Icons.Default.Wifi
        "SportsEsports" -> Icons.Default.SportsEsports
        "LocalHospital" -> Icons.Default.LocalHospital
        "MoreHoriz" -> Icons.Default.MoreHoriz
        else -> Icons.Default.Category
    }
}

@Composable
fun CategoryIconBadge(
    iconName: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 40.dp,
    iconSize: Dp = 22.dp
) {
    val bgColor = parseColorHex(colorHex)
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getCategoryIcon(iconName),
            contentDescription = null,
            tint = bgColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
