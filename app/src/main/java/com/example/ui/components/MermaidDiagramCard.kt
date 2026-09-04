package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.DiagramItem
import com.example.service.GeminiLectureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MermaidDiagramCard(
    diagram: DiagramItem,
    sourceUri: String,
    sourceType: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Digital Mermaid, 1: Frame Capture, 2: Code
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturingFrame by remember { mutableStateOf(false) }

    // Attempt video frame capture if source is uploaded video
    LaunchedEffect(sourceUri, diagram.timestamp) {
        if (sourceType == "UPLOAD" && sourceUri.isNotBlank()) {
            isCapturingFrame = true
            withContext(Dispatchers.IO) {
                try {
                    val service = GeminiLectureService(context)
                    val uri = Uri.parse(sourceUri)
                    capturedBitmap = service.extractFrameAtTimestamp(uri, diagram.timestamp)
                } catch (_: Exception) {}
            }
            isCapturingFrame = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Diagram Label, Type Badge & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = diagram.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = diagram.diagramType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = diagram.timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description
            Text(
                text = diagram.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tab bar: Mermaid Digital / Frame Capture / Code
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .height(38.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Digital Diagram", fontSize = 11.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Video Frame", fontSize = 11.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mermaid Code", fontSize = 11.sp)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Content according to selected tab
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp
            ) {
                when (selectedTab) {
                    0 -> {
                        // Digital Mermaid recreation
                        if (diagram.mermaidCode.isNotBlank()) {
                            MermaidWebView(
                                mermaidCode = diagram.mermaidCode,
                                isDarkTheme = isDark
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Diagram represented as freeform illustration. See 'Video Frame' tab for the visual frame at ${diagram.timestamp}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    1 -> {
                        // Original Frame Capture Fallback
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturingFrame) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Extracting video frame at ${diagram.timestamp}...", style = MaterialTheme.typography.labelSmall)
                                }
                            } else if (capturedBitmap != null) {
                                Image(
                                    bitmap = capturedBitmap!!.asImageBitmap(),
                                    contentDescription = "Extracted lecture video frame at ${diagram.timestamp}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                // Frame placeholder / simulator
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Frame Marker at ${diagram.timestamp}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (sourceType == "YOUTUBE")
                                            "Source is online YouTube lecture: jump to ${diagram.timestamp} in video player to inspect original whiteboard."
                                        else
                                            "Frame capture fallback available for local video recordings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Raw Mermaid Code Inspector
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mermaid Syntax:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(diagram.mermaidCode))
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Mermaid code",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = diagram.mermaidCode.ifBlank { "// No Mermaid code generated" },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(
    mermaidCode: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val themeName = if (isDarkTheme) "dark" else "default"
    val textColor = if (isDarkTheme) "#E3E2E6" else "#1A1B1F"
    val bgColor = if (isDarkTheme) "#1A1B1F" else "#FFFFFF"

    val sanitizedMermaid = mermaidCode
        .replace("\\", "\\\\")
        .replace("`", "\\`")

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=0.9, user-scalable=yes">
          <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
          <style>
            body {
              margin: 0;
              padding: 10px;
              background-color: $bgColor;
              color: $textColor;
              display: flex;
              justify-content: center;
              align-items: center;
              min-height: 100vh;
              box-sizing: border-box;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            }
            .mermaid {
              width: 100%;
              text-align: center;
            }
            svg {
              max-width: 100% !important;
              height: auto !important;
            }
          </style>
        </head>
        <body>
          <div class="mermaid">
$sanitizedMermaid
          </div>
          <script>
            mermaid.initialize({
              startOnLoad: true,
              theme: '$themeName',
              securityLevel: 'loose'
            });
          </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
        }
    )
}
