package com.mooscles.designsystem.format

/**
 * Localizes numbers the domain already rounded. The rounding rule stays in the domain
 * (`number()` in :app, `DecimalText.fixed2` in :shared); this only swaps separators.
 */
object MoosclesNumbers {
    private const val NO_BREAK_SPACE = " "
    private val plain = Regex("-?[0-9]+(\\.[0-9]+)?")

    /** [plain] is a simple decimal such as `-1234.75`; anything else throws. */
    fun localize(plain: String, locale: NumberLocale): String {
        require(this.plain.matches(plain)) { "Not a plain decimal: \"$plain\"" }
        val negative = plain.startsWith('-')
        val unsigned = if (negative) plain.substring(1) else plain
        val integer = unsigned.substringBefore('.')
        val fraction = if ('.' in unsigned) unsigned.substringAfter('.') else null
        val (group, decimal) = when (locale) {
            NumberLocale.EN -> "," to "."
            NumberLocale.PT_BR -> "." to ","
        }
        val grouped = buildString {
            integer.forEachIndexed { index, digit ->
                if (index > 0 && (integer.length - index) % 3 == 0) append(group)
                append(digit)
            }
        }
        return (if (negative) "-" else "") + grouped + (if (fraction != null) decimal + fraction else "")
    }

    /** `82,5 kg`, with a no-break space before the unit. */
    fun weight(plain: String, unit: String, locale: NumberLocale): String =
        localize(plain, locale) + NO_BREAK_SPACE + unit

    /** `87,5 kg × 6`. */
    fun setText(loadPlain: String, unit: String, reps: Int, locale: NumberLocale): String =
        "${weight(loadPlain, unit, locale)} × $reps"

    /** `1:24`, the same in every language. */
    fun clock(seconds: Int): String {
        val rest = seconds % 60
        return "${seconds / 60}:${if (rest < 10) "0" else ""}$rest"
    }
}
