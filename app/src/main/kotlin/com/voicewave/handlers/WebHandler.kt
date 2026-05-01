package com.voicewave.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens web content using Firefox Focus with DuckDuckGo,
 * or the native YouTube/Wikipedia app if installed.
 *
 * HOW DEEP LINKS WORK:
 * Apps register themselves to handle certain URL patterns.
 * YouTube registers for youtube.com URLs — so opening a youtube.com
 * URL fires the YouTube app directly instead of the browser.
 * Same for Wikipedia. If the app isn't installed, it falls back to Firefox Focus.
 */
object WebHandler {

    // DuckDuckGo search URL
    private const val DDG_SEARCH = "https://duckduckgo.com/?q="

    // Firefox Focus package name — used to target it specifically
    private const val FIREFOX_FOCUS_PKG = "org.mozilla.focus"

    fun webSearch(context: Context, query: String): Boolean {
        val url = DDG_SEARCH + Uri.encode(query)
        return openUrl(context, url)
    }

    fun youtube(context: Context, query: String): Boolean {
        // Try YouTube app first via its search intent
        return try {
            val youtubeIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(youtubeIntent)
            true
        } catch (e: Exception) {
            // YouTube app not installed (likely on degoogled phone), fall back to browser
            val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
            openUrl(context, url)
        }
    }

    fun wikipedia(context: Context, query: String): Boolean {
        // Try Wikipedia app first
        return try {
            val wikiAppIntent = Intent(Intent.ACTION_SEND).apply {
                setPackage("org.wikipedia")
                putExtra(Intent.EXTRA_TEXT, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(wikiAppIntent)
            true
        } catch (e: Exception) {
            // Fall back to browser
            val url = "https://en.wikipedia.org/wiki/Special:Search?search=${Uri.encode(query)}"
            openUrl(context, url)
        }
    }

    /**
     * Opens a URL, preferring Firefox Focus.
     * Falls back to whatever the system default browser is.
     */
    private fun openUrl(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)

        // Try Firefox Focus first
        return try {
            val focusIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(FIREFOX_FOCUS_PKG)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(focusIntent)
            true
        } catch (e: Exception) {
            // Firefox Focus not installed, use system default browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
