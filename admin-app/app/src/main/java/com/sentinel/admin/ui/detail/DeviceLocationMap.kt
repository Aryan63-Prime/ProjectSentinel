package com.sentinel.admin.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
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
import androidx.compose.ui.graphics.Color
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            val cleanNetwork = location.network.replace(" (Charging)", "")
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        try {
                            val assetList = ctx.assets.list("")
                            android.util.Log.i("Sentinel:Assets", "Assets list: ${assetList?.joinToString(", ")}")
                        } catch (e: Exception) {
                            android.util.Log.e("Sentinel:Assets", "Failed to list assets", e)
                        }

                        val cssContent = try {
                            val text = ctx.assets.open("leaflet.css").bufferedReader().use { it.readText() }
                            android.util.Log.i("Sentinel:Assets", "Loaded leaflet.css successfully: ${text.length} chars")
                            text
                        } catch (e: Exception) {
                            android.util.Log.e("Sentinel:Assets", "Error loading leaflet.css", e)
                            ""
                        }
                        val jsContent = try {
                            val text = ctx.assets.open("leaflet.js").bufferedReader().use { it.readText() }
                            android.util.Log.i("Sentinel:Assets", "Loaded leaflet.js successfully: ${text.length} chars")
                            text
                        } catch (e: Exception) {
                            android.util.Log.e("Sentinel:Assets", "Error loading leaflet.js", e)
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
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    android.util.Log.d("Sentinel:WebConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                    return true
                                }
                            }
                            
                            val color = if (isOnline) "#2e7d32" else "#d32f2f"
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                    <style>
                                        $cssContent
                                        html, body { height: 100%; margin: 0; padding: 0; background: #121212; }
                                        #map { position: absolute; top: 0; bottom: 0; left: 0; right: 0; background: #121212; }
                                        .leaflet-container { background: #121212; }
                                    </style>
                                    <script>
                                        $jsContent
                                    </script>
                                </head>
                                <body>
                                    <div id="map"></div>
                                    <script>
                                        var map = L.map('map', { zoomControl: false }).setView([${location.latitude}, ${location.longitude}], 16);
                                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                            maxZoom: 19,
                                            attribution: '© OpenStreetMap'
                                        }).addTo(map);
                                        
                                        // Use premium vector circleMarker instead of default image pin
                                        var marker = L.circleMarker([${location.latitude}, ${location.longitude}], {
                                            radius: 8,
                                            color: '#ffffff',
                                            weight: 2,
                                            fillColor: '$color',
                                            fillOpacity: 1.0
                                        }).addTo(map)
                                            .bindPopup('<b>$deviceName</b><br>${location.battery}% · $cleanNetwork')
                                            .openPopup();

                                        var circle = L.circle([${location.latitude}, ${location.longitude}], {
                                            color: '$color',
                                            fillColor: '$color',
                                            fillOpacity: 0.15,
                                            radius: ${location.accuracy}
                                        }).addTo(map);
                                        
                                        function updatePosition(lat, lng, acc, batt, net, online) {
                                            map.invalidateSize();
                                            var newPos = [lat, lng];
                                            map.setView(newPos, 16);
                                            marker.setLatLng(newPos);
                                            marker.setPopupContent('<b>' + '$deviceName' + '</b><br>' + batt + '% · ' + net);
                                            circle.setLatLng(newPos);
                                            circle.setRadius(acc);
                                            var newColor = online ? '#2e7d32' : '#d32f2f';
                                            marker.setStyle({ fillColor: newColor });
                                            circle.setStyle({ color: newColor, fillColor: newColor });
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

                // Clickable transparent overlay to open Google Maps with precise coordinates when tapped
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable {
                            val uri = "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${Uri.encode(deviceName)})"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                    context.startActivity(fallbackIntent)
                                } catch (ex: Exception) {
                                    android.util.Log.e("Sentinel:Map", "Failed to start maps intent", ex)
                                }
                            }
                        }
                )
            }
        }
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
