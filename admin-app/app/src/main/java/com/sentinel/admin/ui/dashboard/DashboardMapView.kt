package com.sentinel.admin.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Map view for the Dashboard showing all device markers using Leaflet.js and OpenStreetMap.
 *
 * - Auto-fit camera to show all markers
 * - Marker click navigates to device detail via JavaScript interface
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun DashboardMapView(
    markers: List<DeviceMarker>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (markers.isEmpty()) {
        NoLocationState(modifier = modifier)
        return
    }

    val markersJson = markers.joinToString(separator = ",", prefix = "[", postfix = "]") { marker ->
        val cleanSnippet = marker.snippet.replace(" (Charging)", "")
        """
        {
            "deviceId": "${marker.deviceId}",
            "deviceName": "${marker.deviceName.replace("'", "\\'")}",
            "snippet": "${cleanSnippet.replace("'", "\\'")}",
            "latitude": ${marker.latitude},
            "longitude": ${marker.longitude},
            "isOnline": ${marker.isOnline}
        }
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val cssContent = try {
                context.assets.open("leaflet.css").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
            val jsContent = try {
                context.assets.open("leaflet.js").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }

            WebView(context).apply {
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
                            "if (typeof updateMarkers === 'function') { updateMarkers($markersJson); }",
                            null
                        )
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onMarkerClick(deviceId: String) {
                        post {
                            onMarkerClick(deviceId)
                        }
                    }
                }, "AndroidInterface")

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
                            var map = L.map('map', { zoomControl: false }).setView([0.0, 0.0], 2);
                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                maxZoom: 19,
                                attribution: '© OpenStreetMap'
                            }).addTo(map);

                            var markersList = {};

                            function updateMarkers(markersData) {
                                map.invalidateSize();
                                // Remove old markers
                                for (var id in markersList) {
                                    map.removeLayer(markersList[id]);
                                }
                                markersList = {};

                                var groupPoints = [];
                                markersData.forEach(function(m) {
                                    var newColor = m.isOnline ? '#2e7d32' : '#d32f2f';
                                    var marker = L.circleMarker([m.latitude, m.longitude], {
                                        radius: 8,
                                        color: '#ffffff',
                                        weight: 2,
                                        fillColor: newColor,
                                        fillOpacity: 1.0
                                    }).addTo(map)
                                        .bindPopup('<b>' + m.deviceName + '</b><br>' + m.snippet);
                                    
                                    marker.on('click', function() {
                                        AndroidInterface.onMarkerClick(m.deviceId);
                                    });
                                    
                                    markersList[m.deviceId] = marker;
                                    groupPoints.push([m.latitude, m.longitude]);
                                });

                                if (groupPoints.length === 1) {
                                    map.setView(groupPoints[0], 15);
                                } else if (groupPoints.length > 1) {
                                    map.fitBounds(groupPoints, { padding: [50, 50] });
                                }
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
                "if (typeof updateMarkers === 'function') { updateMarkers($markersJson); }",
                null
            )
        }
    )
}

@Composable
private fun NoLocationState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No locations available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Devices with location data will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
