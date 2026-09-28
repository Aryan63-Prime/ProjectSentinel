package com.sentinel.admin.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import androidx.compose.ui.viewinterop.AndroidView
import com.sentinel.admin.domain.model.DeviceLocation

/**
 * Map card for the Device Detail screen.
 *
 * Shows device location with a marker and accuracy circle using Leaflet.js and OpenStreetMap.
 * If no location, shows "No location available".
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeviceLocationMap(
    location: DeviceLocation?,
    deviceName: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    if (location == null) {
        NoLocationCard(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val cleanNetwork = location.network.replace(" (Charging)", "")

    fun launchGoogleMaps() {
        val geoUri = "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${Uri.encode(deviceName)})"
        val webUri = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
                    context.startActivity(webIntent)
                } catch (ex: Exception) {
                    android.util.Log.e("Sentinel:Map", "Failed to launch maps", ex)
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121923)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29446E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Title + Accuracy Badge + Open Google Maps button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFAAC7E8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFAAC7E8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LIVE LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFABC2E4),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Accuracy ±${location.accuracy.toInt()}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF76B6FF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Dedicated button to launch native Google Maps
                androidx.compose.material3.FilledTonalButton(
                    onClick = { launchGoogleMaps() },
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF0284C7).copy(alpha = 0.2f),
                        contentColor = Color(0xFF8FB2D8)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Google Maps",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Interactive Leaflet Map Box with Dark Matter tiles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
            ) {
                SpatialMapBackdrop(modifier = Modifier.fillMaxSize())
                AndroidView(
                    modifier = Modifier.fillMaxSize().alpha(.38f),
                    factory = { ctx ->
                        val cssContent = try {
                            ctx.assets.open("leaflet.css").bufferedReader().use { it.readText() }
                        } catch (e: Exception) {
                            ""
                        }
                        val jsContent = try {
                            ctx.assets.open("leaflet.js").bufferedReader().use { it.readText() }
                        } catch (e: Exception) {
                            ""
                        }

                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(
                                        "if (typeof updatePosition === 'function') { updatePosition(${location.latitude}, ${location.longitude}, ${location.accuracy}, ${location.battery}, '$cleanNetwork', $isOnline); }",
                                        null
                                    )
                                }
                            }
                            
                            val pinColor = if (isOnline) "#10B981" else "#64748B"
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                    <style>
                                        $cssContent
                                        html, body { height: 100%; margin: 0; padding: 0; background: #080C14; }
                                        #map { position: absolute; top: 0; bottom: 0; left: 0; right: 0; background: #080C14; }
                                        .leaflet-container { background: #080C14; }

                                        .radar-pin {
                                            width: 16px;
                                            height: 16px;
                                            border-radius: 50%;
                                            background-color: $pinColor;
                                            border: 2px solid #ffffff;
                                            box-shadow: 0 0 12px $pinColor;
                                        }

                                        .leaflet-popup-content-wrapper {
                                            background: rgba(15, 23, 42, 0.95);
                                            color: #F8FAFC;
                                            border: 1px solid rgba(255,255,255,0.15);
                                            border-radius: 12px;
                                            box-shadow: 0 8px 24px rgba(0,0,0,0.6);
                                            backdrop-filter: blur(8px);
                                        }
                                        .leaflet-popup-tip {
                                            background: rgba(15, 23, 42, 0.95);
                                        }
                                    </style>
                                    <script>
                                        $jsContent
                                    </script>
                                </head>
                                <body>
                                    <div id="map"></div>
                                    <script>
                                        var map = L.map('map', { zoomControl: false }).setView([${location.latitude}, ${location.longitude}], 16);
                                        
                                        // Tactical Dark Map Tiles (Carto Dark Matter with retina support)
                                        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                                            maxZoom: 20,
                                            subdomains: 'abcd',
                                            attribution: '© CARTO'
                                        }).addTo(map);

                                        var marker = L.circleMarker([${location.latitude}, ${location.longitude}], {
                                            radius: 8,
                                            color: '#ffffff',
                                            weight: 2,
                                            fillColor: '$pinColor',
                                            fillOpacity: 1.0
                                        }).addTo(map)
                                            .bindPopup('<b>$deviceName</b><br>⚡ ${location.battery}% · $cleanNetwork');

                                        var circle = L.circle([${location.latitude}, ${location.longitude}], {
                                            color: '#00E5FF',
                                            fillColor: '#00E5FF',
                                            fillOpacity: 0.10,
                                            weight: 1.5,
                                            radius: ${location.accuracy}
                                        }).addTo(map);
                                        
                                        function updatePosition(lat, lng, acc, batt, net, online) {
                                            map.invalidateSize();
                                            var newPos = [lat, lng];
                                            map.setView(newPos, 16);
                                            marker.setLatLng(newPos);
                                            marker.setPopupContent('<b>' + '$deviceName' + '</b><br>⚡ ' + batt + '% · ' + net);
                                            circle.setLatLng(newPos);
                                            circle.setRadius(acc);
                                            var newColor = online ? '#10B981' : '#64748B';
                                            marker.setStyle({ fillColor: newColor });
                                        }
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()
                            loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.evaluateJavascript(
                            "if (typeof updatePosition === 'function') { updatePosition(${location.latitude}, ${location.longitude}, ${location.accuracy}, ${location.battery}, '$cleanNetwork', $isOnline); }",
                            null
                        )
                    }
                )

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF388BFF).copy(alpha = .28f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF69B5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Floating quick button inside the map container at bottom-right for instant 1-tap navigation
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD90F172A))
                        .clickable { launchGoogleMaps() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Open in Maps",
                            tint = Color(0xFFAAC7E8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Open Google Maps ↗",
                            color = Color(0xFFF0F2F4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Coordinates Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B1120))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format("%.5f", location.latitude)}, ${String.format("%.5f", location.longitude)}",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF9AA7B6),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = cleanNetwork,
                    fontSize = 11.sp,
                    color = Color(0xFF8FB2D8),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SpatialMapBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF172D4E), Color(0xFF0A192E), Color(0xFF132946)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        val minorRoad = Color(0xFF52749E).copy(alpha = .52f)
        for (index in 0..8) {
            val x = size.width * index / 8f
            drawLine(minorRoad, Offset(x, 0f), Offset((x - size.width * .12f).coerceAtLeast(0f), size.height), 1.dp.toPx())
        }
        for (index in 0..6) {
            val y = size.height * index / 6f
            drawLine(minorRoad, Offset(0f, y), Offset(size.width, y - size.height * .08f), 1.dp.toPx())
        }
        val arterial = Path().apply {
            moveTo(-size.width * .08f, size.height * .72f)
            quadraticTo(size.width * .48f, size.height * .36f, size.width * 1.08f, size.height * .48f)
        }
        drawPath(arterial, Color(0xFF6A9DD5).copy(alpha = .62f), style = Stroke(6.dp.toPx()))
        drawPath(arterial, Color(0xFFC4DCFA).copy(alpha = .35f), style = Stroke(1.dp.toPx()))
        val route = Path().apply {
            moveTo(size.width * .18f, size.height * 1.08f)
            quadraticTo(size.width * .3f, size.height * .55f, size.width * .76f, -size.height * .12f)
        }
        drawPath(route, Color(0xFF4C83C6).copy(alpha = .56f), style = Stroke(4.dp.toPx()))
        drawCircle(Color(0xFF5C9BFF).copy(alpha = .16f), 40.dp.toPx(), Offset(size.width * .67f, size.height * .42f))
        drawCircle(Color(0xFF72B5FF).copy(alpha = .18f), 1.dp.toPx(), Offset(size.width * .67f, size.height * .42f), style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun NoLocationCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No location available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
