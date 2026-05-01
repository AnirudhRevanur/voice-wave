package com.voicewave.parser

/**
 * When the main parser gives up and returns Unknown, this tries to find
 * the closest matching command keyword using edit distance, then builds
 * a human-readable "Did you mean X?" suggestion.
 *
 * HOW IT WORKS:
 * We maintain a list of (trigger keyword → command builder) pairs.
 * For the unknown input, we compute the edit distance from the first 1-3
 * words of the input to each trigger. The closest match below a threshold
 * is offered as a suggestion.
 *
 * Examples:
 *   "opem spotify"   → "open spotify?"         (open threshold=2)
 *   "youtoob lofi"   → "search YouTube for lofi?" (youtube threshold=3)
 *   "coll mum"       → "call mum?"              (call threshold=2)
 *   "wikkipedia cats"→ "search Wikipedia for cats?" (wikipedia threshold=3)
 *   "paws music"     → "pause music?"           (pause threshold=2)
 */
object CommandSuggester {

    // Max edit distance we'll tolerate before giving up on a suggestion.
    // Higher = more suggestions but more false positives.
    private const val GLOBAL_THRESHOLD = 4

    /**
     * Each entry is:
     *   trigger       — the canonical keyword to compare the input against
     *   threshold     — max edit distance for this trigger (tighter for short words)
     *   wordsToMatch  — how many words from the start of input to compare
     *   build         — given the full cleaned input, produce the suggested VoiceCommand
     *                   and a display string. Return null if the args are empty/invalid.
     */
    private data class CandidateRule(
        val trigger: String,
        val threshold: Int,
        val wordsToMatch: Int = 1,
        val build: (input: String) -> Pair<String, VoiceCommand>?
    )

    private val rules = listOf(
        CandidateRule("open", threshold = 2) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "open $arg?" to VoiceCommand.OpenApp(arg)
        },
        CandidateRule("launch", threshold = 2) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "open $arg?" to VoiceCommand.OpenApp(arg)
        },
        CandidateRule("call", threshold = 2) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "call $arg?" to VoiceCommand.Call(arg)
        },
        CandidateRule("whatsapp", threshold = 3) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "WhatsApp $arg?" to VoiceCommand.WhatsApp(arg)
        },
        CandidateRule("youtube", threshold = 3) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "search YouTube for \"$arg\"?" to VoiceCommand.YouTube(arg)
        },
        CandidateRule("wikipedia", threshold = 3) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "search Wikipedia for \"$arg\"?" to VoiceCommand.Wikipedia(arg)
        },
        CandidateRule("search", threshold = 2) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "web search for \"$arg\"?" to VoiceCommand.WebSearch(arg)
        },
        CandidateRule("calculate", threshold = 3) { input ->
            val arg = input.removeFirstWord()
            if (arg.isEmpty()) null
            else "calculate $arg?" to VoiceCommand.Calculate(arg)
        },
        CandidateRule("play", threshold = 2) { _ ->
            "play music?" to VoiceCommand.PlayMusic
        },
        CandidateRule("pause", threshold = 2) { _ ->
            "pause music?" to VoiceCommand.PauseMusic
        },
        CandidateRule("next", threshold = 2) { _ ->
            "next track?" to VoiceCommand.NextTrack
        },
        CandidateRule("previous", threshold = 3) { _ ->
            "previous track?" to VoiceCommand.PreviousTrack
        },
        CandidateRule("find file", threshold = 3, wordsToMatch = 2) { input ->
            val arg = input.removeFirstWord().removeFirstWord()
            if (arg.isEmpty()) null
            else "find file \"$arg\"?" to VoiceCommand.FileSearch(arg)
        }
    )

    /**
     * Returns a [VoiceCommand.Suggestion] if a close-enough match is found,
     * or null if nothing is close enough to suggest.
     */
    fun suggest(input: String): VoiceCommand.Suggestion? {
        if (input.isBlank()) return null

        val words = input.trim().split(" ")

        var bestDistance = Int.MAX_VALUE
        var bestResult: Pair<String, VoiceCommand>? = null

        for (rule in rules) {
            // Compare the first N words of input against the trigger
            val inputPrefix = words.take(rule.wordsToMatch).joinToString(" ")
            val distance = FuzzyMatcher.levenshtein(inputPrefix, rule.trigger)

            if (distance < bestDistance && distance <= rule.threshold && distance <= GLOBAL_THRESHOLD) {
                val built = rule.build(input) ?: continue
                bestDistance = distance
                bestResult = built
            }
        }

        return bestResult?.let { (displayText, command) ->
            VoiceCommand.Suggestion(displayText = displayText, command = command)
        }
    }

    private fun String.removeFirstWord(): String = substringAfter(" ").trim()
}
