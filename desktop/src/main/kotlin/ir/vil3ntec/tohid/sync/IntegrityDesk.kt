package ir.vil3ntec.tohid.sync

import android.content.Context

/**
 *  سنجشِ امضای بسته مالِ APKِ اندروید است. نصابِ کامپیوتر امضای
 *  اندرویدی ندارد؛ قفلِ واقعی همان مجوزِ امضاشدهٔ سرور است
 *  (`License`)، که این‌جا هم بی‌کم‌وکاست سنجیده می‌شود.
 */
object Integrity {
  fun fingerprint(context: Context): String = ""
  fun isGenuine(context: Context): Boolean = true
}
