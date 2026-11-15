package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.hub.HubMdns

/**
 *  جایگزینِ iOS ِ `platform/HubFindAndroid.kt` (SYN-09): پرسشِ چندپخشِ mDNS در iOS مجوزِ ویژهٔ اپل می‌خواهد (همان دلیلِ
 *  `PrinterFinderIos`) ⇒ کشف نیست؛ گوشیِ آیفون با QR ِ کامپیوتر جفت می‌شود (QR نشانی و سنجاق را دارد).
 */
object HubFind {
  fun hubs(context: Context): List<HubMdns.Found> = emptyList()
}
