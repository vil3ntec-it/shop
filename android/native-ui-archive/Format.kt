package af.tohid.shop.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** قالب‌بندی عدد و تاریخ به فارسی — همان چیزی که نسخه وب نشان می‌دهد. */
object Format {

    private val faDigits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun money(value: Double): String {
        val rounded = Math.round(value)
        val grouped = String.format(Locale.US, "%,d", rounded)
        return toFa(grouped.replace(',', '٬'))
    }

    fun number(value: Double): String {
        val text = if (value == Math.floor(value)) Math.round(value).toString()
            else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        return toFa(text)
    }

    fun toFa(text: String): String = buildString {
        for (c in text) append(if (c in '0'..'9') faDigits[c - '0'] else c)
    }

    /** تاریخ ISO (YYYY-MM-DD) که دفتر با آن کار می‌کند. */
    fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isoOf(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    fun shortDate(iso: String): String = toFa(iso)

    fun ago(millis: Long): String {
        if (millis <= 0) return "هرگز"
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < 60_000 -> "همین حالا"
            diff < 3_600_000 -> "${toFa((diff / 60_000).toString())} دقیقه پیش"
            diff < 86_400_000 -> "${toFa((diff / 3_600_000).toString())} ساعت پیش"
            else -> "${toFa((diff / 86_400_000).toString())} روز پیش"
        }
    }
}
