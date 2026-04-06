package org.akvo.afribamodkvalidator.ui.screen

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akvo.afribamodkvalidator.ui.theme.AfriBamODKValidatorTheme
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

private const val ASSET_FILE = "validation-rules.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val htmlState = produceState<String?>(initialValue = null, isDark) {
        value = withContext(Dispatchers.Default) {
            buildHtml(context, isDark)
        }
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface,
                    titleContentColor =
                        MaterialTheme.colorScheme.onSurface
                ),
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        val html = htmlState.value
        if (html == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CircularProgressIndicator()
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef.value = this
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        setBackgroundColor(0)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = true
                        }
                        loadDataWithBaseURL(
                            null, html, "text/html", "UTF-8", null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

private fun buildHtml(context: Context, isDark: Boolean): String {
    val markdown = loadMarkdownFromAssets(context)
    val flavour = GFMFlavourDescriptor()
    val tree = MarkdownParser(flavour)
        .buildMarkdownTreeFromString(markdown)
    val body = HtmlGenerator(markdown, tree, flavour)
        .generateHtml()
    return wrapWithStyle(body, isDark)
}

private fun wrapWithStyle(body: String, isDark: Boolean): String {
    val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
    val headingColor = if (isDark) "#ffffff" else "#111111"
    val borderColor = if (isDark) "#444" else "#ccc"
    val thBg = if (isDark) "#2a2a2a" else "#f0f0f0"
    val thColor = if (isDark) "#ffffff" else "#1a1a1a"
    val evenRowBg = if (isDark) "#1e1e1e" else "#f8f8f8"
    val cellColor = if (isDark) "#e0e0e0" else "#1a1a1a"
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  body {
    font-family: -apple-system, sans-serif;
    font-size: 15px;
    line-height: 1.5;
    padding: 8px 12px;
    margin: 0;
    color: $textColor;
    background: transparent;
  }
  h1, h2, h3 { color: $headingColor; }
  h1 { font-size: 1.4em; margin: 0.8em 0 0.4em; }
  h2 { font-size: 1.25em; margin: 0.7em 0 0.3em; }
  h3 { font-size: 1.1em; margin: 0.6em 0 0.3em; }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 0.8em 0;
    font-size: 0.92em;
  }
  th, td {
    border: 1px solid $borderColor;
    padding: 6px 8px;
    text-align: left;
    color: $cellColor;
  }
  th {
    background: $thBg;
    color: $thColor;
    font-weight: 600;
  }
  tr:nth-child(even) { background: $evenRowBg; }
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()
}

private fun loadMarkdownFromAssets(context: Context): String {
    return try {
        context.assets
            .open(ASSET_FILE)
            .bufferedReader()
            .use { it.readText() }
    } catch (_: Exception) {
        "# Validation Rules\n\nUnable to load content."
    }
}

@Preview(showBackground = true)
@Composable
private fun SupportScreenPreview() {
    AfriBamODKValidatorTheme {
        SupportScreen(onBack = {})
    }
}