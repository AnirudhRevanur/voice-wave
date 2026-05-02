package com.voicewave.parser

/**
 * Cleans up common STT mishearings before anything else touches the text.
 *
 * Think of this as autocorrect, but specifically for voice assistant nonsense.
 * Runs ONCE on the raw Vosk output, before IntentParser sees it.
 *
 * HOW TO ADD NEW ENTRIES:
 * Just add to the REPLACEMENTS map below.
 * Key = what Vosk says. Value = what it actually means.
 * Order doesn't matter — all replacements run on the normalized string.
 */
object TextNormalizer {

    // ── Known STT mishearings ─────────────────────────────────────────────────
    private val REPLACEMENTS = mapOf(
        // Apps
        "what's app"        to "whatsapp",
        "what's up"         to "whatsapp",
        "watts app"         to "whatsapp",
        "watsapp"           to "whatsapp",
        "whatapp"           to "whatsapp",
        "you tube"          to "youtube",
        "u tube"            to "youtube",
        "utube"             to "youtube",
        "you to"            to "youtube",
        "spot if i"         to "spotify",
        "spotty"            to "spotify",
        "net flicks"        to "netflix",
        "net flix"          to "netflix",
        "insta gram"        to "instagram",
        "linked in"         to "linkedin",
        "tick tock"         to "tiktok",
        "tic toc"           to "tiktok",
        "snap chat"         to "snapchat",
        "face book"         to "facebook",

        // Commands
        "such"              to "search",
        "surge"             to "search",
        "find the"          to "find",
        "look up"           to "search",
        "play list"         to "playlist",
        "next song"         to "next",
        "previous song"     to "previous",
        "go back"           to "previous",

        // Math
        "times"             to "*",
        "multiplied by"     to "*",
        "divided by"        to "/",
        "plus"              to "+",
        "minus"             to "-",
    )

    /**
     * Normalize raw STT output.
     * Lowercases, trims, then applies all replacements.
     * Whole-phrase replacements run first, then word-level cleanup.
     */
    fun normalize(raw: String): String {
        var text = raw.lowercase().trim()

        // Replace known bad phrases (longest first to avoid partial clobbers)
        REPLACEMENTS.entries
            .sortedByDescending { it.key.length }
            .forEach { (bad, good) ->
                text = text.replace(bad, good)
            }

        // Collapse any double spaces left behind
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }
}
