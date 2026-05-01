package com.voicewave.handlers

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * Handles spoken math and unit conversion entirely on-device.
 * No internet, no API — pure Kotlin logic.
 *
 * SUPPORTS:
 * Math:
 *   "what's 20 percent of 340"
 *   "500 divided by 7"
 *   "12 times 13"
 *   "square root of 144"
 *   "100 plus 37 minus 5"
 *
 * Conversions:
 *   "convert 5 miles to km"
 *   "80 kg to pounds"
 *   "100 fahrenheit to celsius"
 *   "2 hours to minutes"
 *   "500 ml to cups"
 */
object MathHandler {

    data class Result(val expression: String, val answer: String)

    fun handle(input: String): Result? {
        val text = input.lowercase().trim()
        return tryConversion(text) ?: tryMath(text)
    }

    // ─── Unit Conversions ──────────────────────────────────────────────────────

    private val conversionPatterns = listOf(
        // Distance
        Triple("miles?", "km|kilometers?", { v: Double -> v * 1.60934 }),
        Triple("km|kilometers?", "miles?", { v: Double -> v / 1.60934 }),
        Triple("meters?", "feet|ft", { v: Double -> v * 3.28084 }),
        Triple("feet|ft", "meters?", { v: Double -> v / 3.28084 }),
        Triple("inches?|in", "cm|centimeters?", { v: Double -> v * 2.54 }),
        Triple("cm|centimeters?", "inches?|in", { v: Double -> v / 2.54 }),

        // Weight
        Triple("kg|kilograms?", "pounds?|lbs?", { v: Double -> v * 2.20462 }),
        Triple("pounds?|lbs?", "kg|kilograms?", { v: Double -> v / 2.20462 }),
        Triple("grams?|g", "ounces?|oz", { v: Double -> v * 0.035274 }),
        Triple("ounces?|oz", "grams?|g", { v: Double -> v / 0.035274 }),

        // Temperature (handled separately below)

        // Volume
        Triple("liters?|l", "gallons?", { v: Double -> v * 0.264172 }),
        Triple("gallons?", "liters?|l", { v: Double -> v / 0.264172 }),
        Triple("ml|milliliters?", "cups?", { v: Double -> v / 236.588 }),
        Triple("cups?", "ml|milliliters?", { v: Double -> v * 236.588 }),
        Triple("ml|milliliters?", "fl oz|fluid ounces?", { v: Double -> v * 0.033814 }),

        // Time
        Triple("hours?|hr", "minutes?|min", { v: Double -> v * 60 }),
        Triple("minutes?|min", "seconds?|sec", { v: Double -> v * 60 }),
        Triple("hours?|hr", "seconds?|sec", { v: Double -> v * 3600 }),
        Triple("days?", "hours?|hr", { v: Double -> v * 24 }),
        Triple("weeks?", "days?", { v: Double -> v * 7 }),

        // Data
        Triple("gb|gigabytes?", "mb|megabytes?", { v: Double -> v * 1024 }),
        Triple("mb|megabytes?", "kb|kilobytes?", { v: Double -> v * 1024 }),
        Triple("tb|terabytes?", "gb|gigabytes?", { v: Double -> v * 1024 }),
    )

    private fun tryConversion(text: String): Result? {
        // Match: "convert? <number> <fromUnit> to <toUnit>"
        val convRegex = Regex("""(?:convert\s+)?(\d+(?:\.\d+)?)\s+(.+?)\s+to\s+(.+)""")
        val match = convRegex.find(text) ?: return null

        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromRaw = match.groupValues[2].trim()
        val toRaw = match.groupValues[3].trim()

        // Special case: temperature
        if (fromRaw.contains(Regex("f|fahrenheit")) && toRaw.contains(Regex("c|celsius"))) {
            val result = (value - 32) * 5 / 9
            return Result("$value°F to °C", "${formatNumber(result)}°C")
        }
        if (fromRaw.contains(Regex("c|celsius")) && toRaw.contains(Regex("f|fahrenheit"))) {
            val result = value * 9 / 5 + 32
            return Result("$value°C to °F", "${formatNumber(result)}°F")
        }

        // Find matching conversion rule
        for ((fromPattern, toPattern, converter) in conversionPatterns) {
            if (fromRaw.matches(Regex(fromPattern)) && toRaw.matches(Regex(toPattern))) {
                val result = converter(value)
                return Result("$value $fromRaw to $toRaw", formatNumber(result) + " $toRaw")
            }
        }

        return null
    }

    // ─── Math Expressions ─────────────────────────────────────────────────────

    private fun tryMath(text: String): Result? {
        // Strip filler words
        var expr = text
            .replace(Regex("what'?s|what is|calculate|compute|equals?|equal to"), "")
            .trim()

        // Square root
        val sqrtMatch = Regex("""square root of (\d+(?:\.\d+)?)""").find(expr)
        if (sqrtMatch != null) {
            val n = sqrtMatch.groupValues[1].toDouble()
            return Result("√$n", formatNumber(sqrt(n)))
        }

        // Percentage
        val percentOf = Regex("""(\d+(?:\.\d+)?)\s*percent of\s*(\d+(?:\.\d+)?)""").find(expr)
        if (percentOf != null) {
            val pct = percentOf.groupValues[1].toDouble()
            val total = percentOf.groupValues[2].toDouble()
            return Result("$pct% of $total", formatNumber(pct / 100 * total))
        }

        // Spoken operators → symbols
        expr = expr
            .replace(Regex("\\bplus\\b"), "+")
            .replace(Regex("\\bminus\\b"), "-")
            .replace(Regex("\\btimes\\b|\\bmultiplied by\\b"), "*")
            .replace(Regex("\\bdivided by\\b|\\bover\\b"), "/")
            .replace(Regex("\\bto the power of\\b|\\bpower\\b"), "^")
            .trim()

        // Only proceed if it looks like a math expression
        if (!expr.contains(Regex("[+\\-*/^]"))) return null

        return try {
            val result = evalExpression(expr)
            Result(expr, formatNumber(result))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Simple recursive descent expression evaluator.
     * Handles: +, -, *, /, ^ and parentheses.
     * No external libraries needed.
     */
    private fun evalExpression(expr: String): Double {
        val tokens = expr.replace(" ", "")
        return Parser(tokens).parse()
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parse(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
                val op = input[pos++]
                val right = parseMulDiv()
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parsePow()
            while (pos < input.length && (input[pos] == '*' || input[pos] == '/')) {
                val op = input[pos++]
                val right = parsePow()
                left = if (op == '*') left * right else left / right
            }
            return left
        }

        private fun parsePow(): Double {
            val base = parseUnary()
            return if (pos < input.length && input[pos] == '^') {
                pos++
                base.pow(parseUnary())
            } else base
        }

        private fun parseUnary(): Double {
            if (pos < input.length && input[pos] == '-') {
                pos++
                return -parseAtom()
            }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            if (pos < input.length && input[pos] == '(') {
                pos++ // skip '('
                val result = parseAddSub()
                pos++ // skip ')'
                return result
            }
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            return input.substring(start, pos).toDouble()
        }
    }

    private fun formatNumber(value: Double): String {
        return if (value == kotlin.math.floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            "%.4f".format(value).trimEnd('0').trimEnd('.')
        }
    }
}
