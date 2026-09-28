package com.sentinel.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.sentinel.admin.R
import kotlin.math.min

/** Decorative orbital globe used in the fleet overview. */
@Composable
fun FleetOrb(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(176.dp)) {
        val radius = min(size.width, size.height) * 0.46f
        val center = Offset(size.width * 0.52f, size.height * 0.53f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF425C78), Color(0xFF202D3A), Color.Transparent),
                center = center,
                radius = radius * 1.2f
            ),
            radius = radius * 1.2f,
            center = center
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF2D4054), Color(0xFF121A22), Color(0xFF253443)),
                start = Offset(center.x - radius, center.y - radius),
                end = Offset(center.x + radius, center.y + radius)
            ),
            radius = radius,
            center = center
        )
        drawCircle(Color(0xFFAAC7E8).copy(alpha = .45f), radius, center, style = Stroke(1.dp.toPx()))
        drawOval(
            color = Color(0xFFAAC7E8).copy(alpha = .18f),
            topLeft = Offset(center.x - radius * .38f, center.y - radius),
            size = Size(radius * .76f, radius * 2),
            style = Stroke(1.dp.toPx())
        )
        val orbit = Path().apply {
            moveTo(center.x - radius * 1.05f, center.y + radius * .05f)
            quadraticTo(center.x, center.y - radius * .62f, center.x + radius * 1.05f, center.y + radius * .05f)
        }
        drawPath(orbit, Color(0xFFAAC7E8).copy(alpha = .45f), style = Stroke(1.3.dp.toPx()))
        drawOval(
            color = Color(0xFFAAC7E8).copy(alpha = .16f),
            topLeft = Offset(center.x - radius * 1.23f, center.y - radius * .23f),
            size = Size(radius * 2.46f, radius * .65f),
            style = Stroke(1.dp.toPx())
        )
        listOf(
            Offset(center.x - radius * .64f, center.y - radius * .12f),
            Offset(center.x + radius * .58f, center.y - radius * .44f),
            Offset(center.x + radius * .38f, center.y + radius * .48f)
        ).forEach { point ->
            drawCircle(Color(0xFF38E6C1).copy(alpha = .22f), 8.dp.toPx(), point)
            drawCircle(Color(0xFF54F4D0), 3.dp.toPx(), point)
        }
    }
}

/** Stylized pair of glass and titanium handsets, drawn in vector form for crisp scaling. */
@Composable
fun DeviceArtwork(
    modifier: Modifier = Modifier,
    width: Dp = 78.dp,
    height: Dp = 104.dp,
    model: String = ""
) {
    val resource = if (model.contains("samsung", ignoreCase = true) || model.contains("galaxy", ignoreCase = true)) {
        R.drawable.device_titanium_pair
    } else {
        R.drawable.device_silver_pair
    }
    Image(
        painter = painterResource(resource),
        contentDescription = model.ifBlank { "3D device render" },
        modifier = modifier.size(width, height),
        contentScale = ContentScale.Fit
    )
}
