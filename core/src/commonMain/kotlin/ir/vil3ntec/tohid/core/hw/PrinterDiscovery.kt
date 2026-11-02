package ir.vil3ntec.tohid.core.hw

/**
 *  HW-12 — «راه‌اندازیِ آسان»: پیدا کردنِ خودکارِ چاپگرِ شبکه و بلوتوث. فقط منطق؛ سوکت، بلوتوث و
 *  کارتِ شبکه در پرونده‌های مرزیِ برنامه‌اند (`hw/PrinterFinderAndroid.kt`، کامپیوتر `PrinterFinderDesktop.kt`).
 *
 *  ── ایمنی ───────────────────────────────────────────────────────────
 *    • فقط زیرشبکهٔ همان کارتِ وای‌فای/LAN ِ فعال، و فقط اگر نشانی خصوصی باشد (RFC1918):
 *      ‎10/8، ‎172.16/12، ‎192.168/16. نشانیِ همگانی، CGNAT (‎100.64/10)، link-local و هر چیزِ دیگر ⇒ هیچ.
 *    • زیرشبکهٔ بزرگ‌تر از ‎/24 به همان ‎/24 ِ خودِ دستگاه کوچک می‌شود ⇒ حداکثر ۲۵۴ نشانی.
 *    • فقط درگاهِ ۹۱۰۰ (RAW/JetDirect)؛ فقط وصل شدنِ TCP، **هیچ بایتی فرستاده نمی‌شود** (چاپگرِ لیزری با
 *      بایتِ ESC/POS برگهٔ بی‌معنا می‌زند).
 *    • هم‌زمانی محدود ([NetScan.CONCURRENCY]) و مهلتِ هر وصل ([NetScan.CONNECT_TIMEOUT_MS]).
 */
object Subnet {

  const val MAX_HOSTS = 254

  /** کارتِ شبکهٔ فعال: نام (`wlan0`، `en0`…)، نشانیِ IPv4 و طولِ پیشوند */
  data class Lan(val name: String, val ip: String, val prefix: Int)

  /** فقط RFC1918 — زیرشبکه‌ای که هرگز به اینترنت نمی‌رود */
  fun isPrivate(ip: String): Boolean {
    val v = LocalNetwork.ipv4(ip.trim()) ?: return false
    return inV4(v, 10L shl 24, 8) || inV4(v, (172L shl 24) or (16L shl 16), 12) || inV4(v, (192L shl 24) or (168L shl 16), 16)
  }

  private fun inV4(ip: Long, base: Long, bits: Int): Boolean = (ip shr (32 - bits)) == (base shr (32 - bits))

  fun format(v: Long): String = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

  /**
   *  نشانی‌هایی که باید آزموده شوند، به ترتیب. نشانیِ همگانی یا نادرست ⇒ هیچ. پیشوندِ کوچک‌تر از ۲۴
   *  ⇒ همان ‎/24 ِ خودِ دستگاه. نشانیِ شبکه، پخشِ همگانی و خودِ دستگاه کنار می‌روند. هرگز بیش از [max].
   */
  fun hosts(ip: String, prefix: Int, max: Int = MAX_HOSTS): List<String> {
    val self = LocalNetwork.ipv4(ip.trim()) ?: return emptyList()
    if (!isPrivate(ip) || prefix !in 0..32 || max <= 0) return emptyList()
    val bits = prefix.coerceAtLeast(24)
    if (bits == 32) return emptyList()
    val size = 1L shl (32 - bits)
    val base = self and (0xFFFF_FFFFL xor (size - 1))
    val range = if (bits == 31) (base until base + size) else (base + 1 until base + size - 1)
    return range.asSequence().filter { it != self && isPrivate(format(it)) }.take(max.coerceAtMost(MAX_HOSTS)).map(::format).toList()
  }

  /** کارتِ شبکه‌ای که به احتمالِ زیاد ماشینِ مجازی، داکر یا VPN است — جست‌وجو نمی‌شود */
  fun likelyVirtual(name: String): Boolean {
    val n = name.lowercase()
    return VIRTUAL.any { n.startsWith(it) || n.contains(" $it") } || n.contains("virtual") || n.contains("vmware") ||
      n.contains("hyper-v") || n.contains("vpn")
  }

  private val VIRTUAL = listOf("docker", "veth", "br-", "virbr", "vbox", "vmnet", "tun", "tap", "wg", "zt", "utun", "vethernet")

  /** کارت‌های قابلِ جست‌وجو: خصوصی، غیرِ مجازی، یکتا بر پایهٔ زیرشبکه */
  fun usable(all: List<Lan>): List<Lan> =
    all.filter { isPrivate(it.ip) && !likelyVirtual(it.name) && it.prefix in 1..31 }
      .distinctBy { l -> (LocalNetwork.ipv4(l.ip) ?: 0L) shr (32 - l.prefix.coerceAtLeast(24)) }
}

/** برنامهٔ جست‌وجو: دسته‌های هم‌زمان، تا هیچ‌گاه بیش از [CONCURRENCY] سوکت باز نباشد */
object NetScan {
  const val PORT = 9100
  const val CONCURRENCY = 32
  const val CONNECT_TIMEOUT_MS = 400

  fun batches(hosts: List<String>, concurrency: Int = CONCURRENCY): List<List<String>> =
    hosts.chunked(concurrency.coerceIn(1, CONCURRENCY))

  /** بدترین زمان (هیچ‌کدام پاسخ ندهد): دسته‌ها × مهلت — ‎/24 ⇒ ۸ × ۴۰۰ میلی‌ثانیه */
  fun worstCaseMs(hostCount: Int, concurrency: Int = CONCURRENCY, timeoutMs: Int = CONNECT_TIMEOUT_MS): Long {
    val c = concurrency.coerceIn(1, CONCURRENCY)
    return ((hostCount + c - 1) / c).toLong() * timeoutMs
  }

  /** وضعیتِ جست‌وجو برای نوارِ پیشرفت و پیامِ فارسی */
  data class Progress(val total: Int, val done: Int, val found: Int, val finished: Boolean = false) {
    val message: String get() = when {
      total == 0 -> "شبکهٔ وای‌فای یا LAN ِ خصوصی پیدا نشد — دستگاه به شبکهٔ دکان وصل است؟"
      !finished -> "بررسیِ نشانیِ $done از $total… ($found چاپگر)"
      found == 0 -> "در $total نشانیِ این شبکه چاپگری با درگاهِ ۹۱۰۰ پیدا نشد — چاپگر روشن و به همین شبکه وصل است؟"
      else -> "$found چاپگر در $total نشانی پیدا شد"
    }
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
  }
}

/** یک یافته، چه از شبکه چه از بلوتوث؛ [link] همان رشتهٔ `printer_address` (`net:…`، `bt:…`) */
data class FoundPrinter(
  val link: String,
  val name: String,
  val via: Via,
  /** ردهٔ بلوتوث یا نام می‌گوید چاپگر است */
  val printerLike: Boolean = true,
) {
  enum class Via { NETWORK, BT_PAIRED, BT_NEARBY }

  companion object {
    fun network(host: String, port: Int = NetScan.PORT) = FoundPrinter("net:$host:$port", host, Via.NETWORK)
  }
}

object Discovery {

  /**
   *  کلیدِ یکتایی — همان قاعدهٔ `ThermalPrinter.Link.parse`: بی‌پیشوند یعنی بلوتوث (نسخه‌های قدیمی MAC را
   *  لخت می‌نوشتند)، درگاهِ نانوشته ۹۱۰۰. MAC و نامِ میزبان بی بزرگ/کوچک.
   */
  fun key(link: String): String {
    val s = link.trim()
    return when {
      s.startsWith("net:") -> {
        val rest = s.removePrefix("net:")
        val host = rest.substringBeforeLast(':', rest).lowercase()
        val port = rest.substringAfterLast(':', "").toIntOrNull() ?: NetScan.PORT
        "net:$host:$port"
      }
      s.startsWith("usb:") -> s
      else -> "bt:" + s.removePrefix("bt:").uppercase()
    }
  }

  /** نامِ بهتر: نامِ واقعی بر نشانیِ خالی برتری دارد */
  private fun better(a: FoundPrinter, b: FoundPrinter): FoundPrinter {
    fun named(f: FoundPrinter) = f.name.isNotBlank() && !f.link.contains(f.name.trim(), ignoreCase = true)
    val pick = if (!named(a) && named(b)) b else a
    return pick.copy(
      printerLike = a.printerLike || b.printerLike,
      //  جفت‌شده بر «نزدیک» برتری دارد: دیگر جفت کردن لازم نیست
      via = if (a.via == FoundPrinter.Via.BT_PAIRED || b.via == FoundPrinter.Via.BT_PAIRED) FoundPrinter.Via.BT_PAIRED else pick.via,
    )
  }

  /** یکی کردنِ یافته‌ها (هر نشانی یک بار)؛ ترتیب: چاپگرها اول، بعد شبکه، جفت‌شده، نزدیک، بعد نام */
  fun merge(current: List<FoundPrinter>, incoming: List<FoundPrinter>): List<FoundPrinter> {
    val out = LinkedHashMap<String, FoundPrinter>()
    (current + incoming).forEach { f ->
      val k = key(f.link)
      out[k] = out[k]?.let { better(it, f) } ?: f
    }
    return out.values.sortedWith(compareBy<FoundPrinter>({ !it.printerLike }, { it.via.ordinal }, { it.name.lowercase() }))
  }

  /** این یافته پیش‌تر در فهرستِ چاپگرها ثبت شده؟ */
  fun known(found: FoundPrinter, savedLinks: Collection<String>): Boolean {
    val k = key(found.link)
    return savedLinks.any { key(it) == k }
  }
}

/**
 *  ردهٔ دستگاهِ بلوتوث (Class of Device) — بیت‌های ۸ تا ۱۲ ردهٔ اصلی. «Imaging» (‎0x0600) با بیتِ چاپگر
 *  (‎0x80) چاپگر است. چاپگرهای ارزان اغلب ردهٔ «نامشخص» (‎0x1F00) یا «گوناگون» (‎0x0000) می‌گویند؛ پس
 *  آن‌ها می‌مانند و فقط رده‌ای که روشن است چاپگر نیست (گوشی، رایانه، صدا، ماوس/صفحه‌کلید…) کنار می‌رود.
 */
object BtClass {
  const val MAJOR_MASK = 0x1F00
  const val IMAGING = 0x0600
  const val PRINTER_BIT = 0x80
  const val UNCATEGORIZED = 0x1F00
  const val MISC = 0x0000

  private val NAME_HINTS = listOf("print", "pos", "rpp", "mtp", "mpt", "xp-", "zj", "thermal", "receipt", "label")

  fun nameLooksLikePrinter(name: String): Boolean {
    val n = name.lowercase()
    return NAME_HINTS.any { n.contains(it) }
  }

  /** چاپگر است ([true])، شاید ([null] — ردهٔ نامشخص و نامِ بی‌نشانه)، یا روشن است که نیست ([false]) */
  fun printer(deviceClass: Int, name: String): Boolean? {
    val major = deviceClass and MAJOR_MASK
    return when {
      major == IMAGING && (deviceClass and PRINTER_BIT) != 0 -> true
      nameLooksLikePrinter(name) -> true
      major == UNCATEGORIZED || major == MISC || major == IMAGING -> null
      else -> false
    }
  }
}

/**
 *  «چاپِ شناسایی» برای چاپگرِ تازه‌یافته: فقط متنِ ASCII (پیش از ثبت پروفایلی نیست و این چاپگرها جدولِ
 *  نویسهٔ فارسی ندارند) — نامِ برنامه و نشانی، تا روشن شود کدام چاپگرِ روی پیشخان همین نشانی است.
 */
object EasyTestPrint {
  private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
  private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
  private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
  private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)

  /** فقط نویسهٔ دیدنیِ ASCII و فاصله؛ هر چیزِ دیگر ⇒ «?»، حداکثر ۴۰ نویسه (پهنای ۸۰ میلی‌متر) */
  fun ascii(s: String): String = s.map { if (it.code in 32..126) it else '?' }.joinToString("").take(40)

  private fun line(s: String): ByteArray = (ascii(s) + "\n").encodeToByteArray()

  fun bytes(address: String, cut: CutMode = CutMode.PARTIAL): ByteArray {
    val b = EscPos.Bytes()
    b.add(EscPos.INIT)
    b.add(ALIGN_CENTER); b.add(BOLD_ON); b.add(line("TOHID")); b.add(BOLD_OFF)
    b.add(line("Printer found / test"))
    b.add(line(address))
    b.add(ALIGN_LEFT)
    b.add(line("If you can read this, the link works."))
    b.add(EscPos.feed(4))
    b.add(EscPos.cut(cut))
    return b.toByteArray()
  }
}
