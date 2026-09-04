package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.FormulaItem

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KatexFormulaCard(
    formula: FormulaItem,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var showRawLatex by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Formula Name & Copy / Toggle
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
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formula.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (showRawLatex) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showRawLatex = !showRawLatex }
                    ) {
                        Text(
                            text = if (showRawLatex) "Rendered" else "LaTeX",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(formula.latex))
                            copied = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy LaTeX formula",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mathematical Display Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp
            ) {
                if (showRawLatex) {
                    Text(
                        text = formula.latex,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KaTeXWebView(
                            latex = formula.latex,
                            isDarkTheme = isDark
                        )
                    }
                }
            }

            if (formula.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = formula.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXWebView(
    latex: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val textColor = if (isDarkTheme) "#E3E2E6" else "#1A1B1F"
    val bgColor = if (isDarkTheme) "#1A1B1F" else "#FFFFFF"

    val escapedLatex = latex
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
          <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
          <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
          <style>
            body {
              margin: 0;
              padding: 6px 0;
              background-color: transparent;
              color: $textColor;
              display: flex;
              justify-content: center;
              align-items: center;
              font-family: sans-serif;
              overflow: hidden;
            }
            #math {
              font-size: 1.18rem;
              text-align: center;
            }
          </style>
        </head>
        <body>
          <div id="math"></div>
          <script>
            try {
              katex.render("$escapedLatex", document.getElementById('math'), {
                throwOnError: false,
                displayMode: true
              });
            } catch (e) {
              document.getElementById('math').innerText = "$escapedLatex";
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0) // Transparent
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://cdn.jsdelivr.net", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", htmlContent, "text/html", "UTF-8", null)
        }
    )
}
