package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.hub.HubMdns
import ir.vil3ntec.tohid.core.hw.MdnsProbe
import ir.vil3ntec.tohid.hw.PrinterFinderPlatform

/**
 *  SYN-09 — پیدا کردنِ هابِ کامپیوتر در شبکهٔ دکان: پرسشِ یک‌بارهٔ mDNS از هر کارتِ شبکه (همان `MdnsProbe` ِ چاپگر، هدفِ jvm ِ
 *  core؛ اندروید و کامپیوتر). کشف فقط نشانی می‌دهد؛ اعتماد از جفت‌سازی است (`HubPairClient`). آیفون: `HubFindIos.kt` (بی کشف؛ QR).
 */
object HubFind {
  fun hubs(context: Context): List<HubMdns.Found> =
    PrinterFinderPlatform.lanInterfaces(context.applicationContext).flatMap { lan ->
      runCatching { HubMdns.hubs(MdnsProbe.messages(lan.ip, service = HubMdns.SERVICE)) }.getOrDefault(emptyList())
    }.distinctBy { it.ip to it.port }
}
