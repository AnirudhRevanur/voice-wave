package com.voicewave.parser

/**
 * Takes spoken text and figures out what the user wants.
 *
 * HOW PARSING WORKS:
 * We check the input against keyword patterns in priority order.
 * Fuzzy matching (via FuzzyMatcher) means typos and mumbling still work.
 *
 * Priority order matters — more specific patterns come first.
 * "youtube lofi" must match YouTube before it matches web search.
 *
 * COMMANDS:
 *   open/launch <app>
 *   call <contact>
 *   whatsapp <contact>
 *   play/pause/next/previous
 *   youtube <query>
 *   wikipedia <query>
 *   search [the web] for <query>
 *   find file <query> / search file <query>
 *   what's <math> / calculate <math>
 *   convert <value> <from> to <to>
 */
object IntentParser {

    fun parse(input: String): VoiceCommand {
        val text = input.lowercase().trim().replace(Regex("[^a-z0-9% .'']"), " ")
            .replace(Regex("\\s+"), " ").trim()

        // ── App launching ─────────────────────────────────────────────────────
        if (startsWithFuzzy(text, "open", "launch")) {
            val appName = text.removeFirstWord().trim()
            if (appName.isNotEmpty()) return VoiceCommand.OpenApp(appName)
        }

        // ── Calls ─────────────────────────────────────────────────────────────
        if (startsWithFuzzy(text, "call", "phone", "dial", "ring")) {
            val name = text.removeFirstWord().trim()
            if (name.isNotEmpty()) return VoiceCommand.Call(name)
        }

        // ── WhatsApp ──────────────────────────────────────────────────────────
        if (FuzzyMatcher.containsFuzzy(text.split(" ").first(), "whatsapp", threshold = 2)) {
            val name = text.removeFirstWord().trim()
            if (name.isNotEmpty()) return VoiceCommand.WhatsApp(name)
        }

        // ── YouTube (before generic web search) ───────────────────────────────
        if (FuzzyMatcher.containsFuzzy(text.split(" ").first(), "youtube", threshold = 2)) {
            val query = text.removeFirstWord().trim()
            if (query.isNotEmpty()) return VoiceCommand.YouTube(query)
        }
        if (text.contains("youtube")) {
            val query = text
                .replace(Regex(".*youtube.*?for\\s+"), "")
                .replace(Regex(".*(?:search|play).*?youtube\\s*"), "")
                .trim()
            if (query.isNotEmpty()) return VoiceCommand.YouTube(query)
        }

        // ── Wikipedia ────────────────────────────────────────────────────────
        if (FuzzyMatcher.containsFuzzy(text.split(" ").first(), "wikipedia", threshold = 3)) {
            val query = text.removeFirstWord().trim()
            if (query.isNotEmpty()) return VoiceCommand.Wikipedia(query)
        }
        if (text.contains("wikipedia")) {
            val query = text.replace(Regex(".*wikipedia.*?for\\s+|.*wikipedia\\s+"), "").trim()
            if (query.isNotEmpty()) return VoiceCommand.Wikipedia(query)
        }

        // ── File search ───────────────────────────────────────────────────────
        val fileKeywords = listOf("find file", "search file", "find my file", "look for file",
            "search for file", "find document", "search document")
        for (keyword in fileKeywords) {
            if (FuzzyMatcher.levenshtein(text.take(keyword.length + 3), keyword) <= 3) {
                val query = text.drop(keyword.length).trim()
                if (query.isNotEmpty()) return VoiceCommand.FileSearch(query)
            }
        }

        // ── Web search ────────────────────────────────────────────────────────
        val webPrefixes = listOf(
            "search the web for", "search for", "search web for",
            "google", "look up", "look for", "find"
        )
        for (prefix in webPrefixes) {
            if (text.startsWith(prefix) || FuzzyMatcher.levenshtein(text.take(prefix.length + 2), prefix) <= 2) {
                val query = text.drop(prefix.length).trim()
                if (query.isNotEmpty()) return VoiceCommand.WebSearch(query)
            }
        }

        // ── Math & conversions ────────────────────────────────────────────────
        val mathTriggers = listOf("what's", "whats", "calculate", "compute",
            "what is", "convert", "square root")
        val hasMathTrigger = mathTriggers.any { trigger ->
            text.startsWith(trigger) || FuzzyMatcher.levenshtein(text.take(trigger.length + 2), trigger) <= 2
        }
        val looksLikeMath = text.contains(Regex("""\d""")) &&
                text.contains(Regex("plus|minus|times|divided|percent|convert|to|root"))

        if (hasMathTrigger || looksLikeMath) {
            return VoiceCommand.Calculate(text)
        }

        // ── Media controls ────────────────────────────────────────────────────
        val playWords = listOf("play", "resume", "start music", "play music")
        val pauseWords = listOf("pause", "stop", "pause music", "stop music")
        val nextWords = listOf("next", "skip", "next song", "next track")
        val prevWords = listOf("previous", "back", "go back", "previous song", "last song")

        if (playWords.any { FuzzyMatcher.levenshtein(text, it) <= 2 }) return VoiceCommand.PlayMusic
        if (pauseWords.any { FuzzyMatcher.levenshtein(text, it) <= 2 }) return VoiceCommand.PauseMusic
        if (nextWords.any { FuzzyMatcher.levenshtein(text, it) <= 2 }) return VoiceCommand.NextTrack
        if (prevWords.any { FuzzyMatcher.levenshtein(text, it) <= 2 }) return VoiceCommand.PreviousTrack

        // ── Did you mean? ─────────────────────────────────────────────────────
        // Nothing matched — try to find the closest command keyword and suggest it
        val suggestion = CommandSuggester.suggest(text)
        if (suggestion != null) return suggestion

        return VoiceCommand.Unknown(text)
    }

    private fun startsWithFuzzy(text: String, vararg keywords: String): Boolean {
        val firstWord = text.split(" ").first()
        return keywords.any { keyword ->
            FuzzyMatcher.levenshtein(firstWord, keyword) <= 1
        }
    }

    private fun String.removeFirstWord(): String =
        this.substringAfter(" ").trim()
}

/**
 * All possible voice commands as a sealed class.
 * Each one carries exactly the data its handler needs.
 */
sealed class VoiceCommand {
    data class OpenApp(val appName: String) : VoiceCommand()
    data class Call(val contactName: String) : VoiceCommand()
    data class WhatsApp(val contactName: String) : VoiceCommand()
    data class YouTube(val query: String) : VoiceCommand()
    data class Wikipedia(val query: String) : VoiceCommand()
    data class WebSearch(val query: String) : VoiceCommand()
    data class FileSearch(val query: String) : VoiceCommand()
    data class Calculate(val expression: String) : VoiceCommand()
    object PlayMusic : VoiceCommand()
    object PauseMusic : VoiceCommand()
    object NextTrack : VoiceCommand()
    object PreviousTrack : VoiceCommand()
    /**
     * The parser didn't match, but found a close-enough command keyword.
     * [displayText] is the human-readable suggestion shown in the overlay,
     * e.g. "open <app name>?" or "search YouTube for <query>?"
     * [command] is the fully resolved command that will execute if confirmed.
     */
    data class Suggestion(val displayText: String, val command: VoiceCommand) : VoiceCommand()
    data class Unknown(val rawInput: String) : VoiceCommand()
}
