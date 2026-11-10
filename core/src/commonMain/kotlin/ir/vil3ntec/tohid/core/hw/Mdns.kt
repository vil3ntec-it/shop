package ir.vil3ntec.tohid.core.hw

/**
 *  HW-12 — پیدا کردنِ چاپگرِ شبکه با mDNS/DNS-SD (RFC 6762، RFC 6763)، کنارِ جست‌وجوی درگاهِ ۹۱۰۰ ِ `NetScan`.
 *  فقط منطق (بایت ⇐ رکورد ⇐ چاپگر)؛ سوکت در `MdnsProbe` (jvmMain، اندروید و کامپیوتر).
 *
 *  ── چه پرسیده می‌شود ─────────────────────────────────────────────────
 *    فقط `_pdl-datastream._tcp` — چاپگرِ «خام» (RAW/JetDirect): همان درگاهی که ESC/POS را بی‌واسطه می‌گیرد و
 *    SRV ِ آن درگاهِ درست را می‌گوید (گاهی جز ۹۱۰۰). `_ipp._tcp` و `_printer._tcp` پرسیده نمی‌شوند: درگاهشان
 *    (۶۳۱، ۵۱۵) ESC/POS نمی‌گیرد و حدسِ «همان میزبان، ۹۱۰۰» چاپگرِ لیزری را به برگهٔ بی‌معنا می‌کشاند.
 *  ── چگونه ─────────────────────────────────────────────────────────────
 *    پرسشِ یک‌باره (§5.1) از درگاهِ گذرا، نه ۵۳۵۳: پاسخ‌دهندهٔ درست باید تک‌پخش به همان درگاه و با همان
 *    شناسه پاسخ دهد (§6.7، «legacy unicast»)؛ پس نه عضویتِ گروه لازم است نه قفلِ چندپخشِ وای‌فای. پاسخ‌دهنده‌ای
 *    که فقط چندپخش می‌کند پیدا نمی‌شود — جست‌وجوی ۹۱۰۰ همچنان هست.
 *  ── ایمنی ─────────────────────────────────────────────────────────────
 *    • هر بستهٔ بدشکل هیچ است: اشارهٔ فشرده‌سازی فقط به پیش‌تر (حلقه ممکن نیست)، نام تا ۲۵۵ بایت، دادهٔ
 *      رکورد درونِ بسته، پاسخِ خطا یا opcode ِ دیگر ⇒ `null`. هیچ استثنایی از بایتِ غریبه بیرون نمی‌آید.
 *    • از پاسخ‌ها فقط نشانیِ IPv4 ِ خصوصیِ RFC1918 (`Subnet.isPrivate`)، همان مرزِ جست‌وجوی ۹۱۰۰.
 *    • رکوردِ TTL ِ صفر («خداحافظ»، §10.1) چاپگر نیست. نامِ نمایشی بی نویسهٔ کنترلی و جهت‌دهندهٔ پنهان.
 *  بردارها: `core/test-vectors/mdns-v1.json` (ساختهٔ dnspython؛ `MdnsVectorsTest`، `server/test/hw-mdns.test.js`).
 *  روی چاپگرِ واقعی آزموده نشده.
 */
object Mdns {
  const val PDL = "_pdl-datastream._tcp.local"
  const val GROUP = "224.0.0.251"
  const val PORT = 5353

  /** چشم‌به‌راهِ پاسخ پس از پرسش — پاسخ‌دهنده تا ۵۰۰ میلی‌ثانیه درنگ می‌کند (§6) */
  const val WAIT_MS = 1_500

  /** سقفِ بسته‌هایی که از یک پرسش خوانده می‌شود */
  const val MAX_PACKETS = 64

  const val TYPE_A = 1
  const val TYPE_PTR = 12
  const val TYPE_TXT = 16
  const val TYPE_AAAA = 28
  const val TYPE_SRV = 33
  private const val CLASS_IN = 1
  private const val MAX_JUMPS = 64

  /** نام‌ها برچسب‌به‌برچسب‌اند (برچسبِ نامِ چاپگر خودش می‌تواند «.» داشته باشد)؛ بی برچسبِ ریشه */
  data class Record(
    val name: List<String>,
    val type: Int,
    val ttl: Long,
    val ptr: List<String> = emptyList(),
    val target: List<String> = emptyList(),
    val port: Int = 0,
    val ipv4: String = "",
  )

  /** فقط PTR، SRV و A ِ ردهٔ IN نگه داشته می‌شوند (بیتِ cache-flush ِ §10.2 کنار) */
  data class Message(val id: Int, val response: Boolean, val records: List<Record>)

  /** پرسشِ PTR (ردهٔ IN، بی بیتِ QU) با شناسهٔ [id] */
  fun query(service: String = PDL, id: Int = 0): ByteArray {
    val out = EscPos.Bytes()
    out.add(byteArrayOf(((id shr 8) and 0xFF).toByte(), (id and 0xFF).toByte(), 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
    for (label in service.split('.')) {
      val b = label.encodeToByteArray()
      require(b.size in 1..63) { "برچسبِ نادرست در نامِ خدمت: $label" }
      out.add(byteArrayOf(b.size.toByte()))
      out.add(b)
    }
    out.add(byteArrayOf(0, 0, TYPE_PTR.toByte(), 0, CLASS_IN.toByte()))
    return out.toByteArray()
  }

  /** بسته ⇒ پیام؛ هر بدشکلی، پاسخِ خطا (RCODE ≠ 0) یا opcode ِ جز ۰ ⇒ `null` */
  fun parse(p: ByteArray): Message? {
    if (p.size < 12) return null
    val flags = u16(p, 2)
    if ((flags and 0x7800) != 0 || (flags and 0x000F) != 0) return null
    val questions = u16(p, 4)
    val total = u16(p, 6) + u16(p, 8) + u16(p, 10)
    var at = 12
    repeat(questions) {
      val n = name(p, at) ?: return null
      at = n.end + 4
      if (at > p.size) return null
    }
    val records = ArrayList<Record>()
    repeat(total) {
      val n = name(p, at) ?: return null
      at = n.end
      if (at + 10 > p.size) return null
      val type = u16(p, at)
      val cls = u16(p, at + 2) and 0x7FFF
      val ttl = u32(p, at + 4)
      val len = u16(p, at + 8)
      at += 10
      if (at + len > p.size) return null
      val rd = at
      val stop = at + len
      at = stop
      if (cls != CLASS_IN) return@repeat
      when (type) {
        TYPE_PTR -> {
          val t = name(p, rd)?.takeIf { it.end <= stop } ?: return null
          records += Record(n.labels, type, ttl, ptr = t.labels)
        }
        TYPE_SRV -> {
          if (len < 7) return null
          val t = name(p, rd + 6)?.takeIf { it.end <= stop } ?: return null
          records += Record(n.labels, type, ttl, target = t.labels, port = u16(p, rd + 4))
        }
        TYPE_A -> {
          if (len != 4) return null
          val ip = (0 until 4).joinToString(".") { (p[rd + it].toInt() and 0xFF).toString() }
          records += Record(n.labels, type, ttl, ipv4 = ip)
        }
      }
    }
    return Message(u16(p, 0), (flags and 0x8000) != 0, records)
  }

  /**
   *  پاسخ‌ها ⇒ چاپگرها: PTR ِ [service] ⇐ نامِ نمونه ⇐ SRV (درگاه و میزبان) ⇐ A ِ همان میزبان (شاید در بستهٔ
   *  دیگر). فقط پاسخ (نه پرسش)، TTL ِ مثبت و IPv4 ِ خصوصی؛ یکتا و به ترتیبِ `Discovery.merge`.
   */
  fun printers(messages: List<Message>, service: String = PDL): List<FoundPrinter> {
    val svc = service.split('.')
    val all = messages.filter { it.response }.flatMap { it.records }
    val instances = ArrayList<List<String>>()
    for (r in all) {
      if (r.type != TYPE_PTR || r.ttl == 0L || !same(r.name, svc)) continue
      if (r.ptr.size != svc.size + 1 || !same(r.ptr.drop(1), svc)) continue
      if (instances.none { same(it, r.ptr) }) instances += r.ptr
    }
    val found = instances.mapNotNull { inst ->
      val srv = all.firstOrNull { it.type == TYPE_SRV && it.ttl > 0 && it.port > 0 && same(it.name, inst) } ?: return@mapNotNull null
      val a = all.firstOrNull { it.type == TYPE_A && it.ttl > 0 && same(it.name, srv.target) && Subnet.isPrivate(it.ipv4) }
        ?: return@mapNotNull null
      FoundPrinter.network(a.ipv4, srv.port).copy(name = clean(inst[0]).ifBlank { a.ipv4 })
    }
    return Discovery.merge(emptyList(), found)
  }

  /** نامِ نمایشی: نویسهٔ کنترلی و جهت‌دهندهٔ پنهان (LRM/RLM، U+202A..E، U+2066..9) ⇒ فاصله */
  fun clean(s: String): String = buildString {
    for (c in s) {
      val k = c.code
      val hidden = k < 32 || k in 127..159 || k in 0x202A..0x202E || k in 0x2066..0x2069 || k == 0x200E || k == 0x200F
      append(if (hidden) ' ' else c)
    }
  }.trim()

  private class Name(val labels: List<String>, val end: Int)

  /**
   *  نام از [start] با فشرده‌سازی (RFC 1035 §4.1.4). `end` جای پس از نام در خودِ رکورد است (پس از نخستین
   *  اشاره). اشاره فقط به پیش‌تر ⇒ هر پرش عقب‌تر می‌رود و حلقه ممکن نیست؛ با این همه سقفِ پرش و گام هست.
   */
  private fun name(p: ByteArray, start: Int): Name? {
    val labels = ArrayList<String>()
    var at = start
    var end = -1
    var jumps = 0
    var wire = 0
    var steps = 0
    while (steps++ < 512) {
      if (at < 0 || at >= p.size) return null
      val len = p[at].toInt() and 0xFF
      when (len and 0xC0) {
        0x00 -> {
          if (len == 0) return Name(labels, if (end < 0) at + 1 else end)
          if (at + 1 + len > p.size) return null
          wire += len + 1
          if (wire > 254) return null
          labels += p.decodeToString(at + 1, at + 1 + len)
          at += 1 + len
        }
        0xC0 -> {
          if (at + 1 >= p.size) return null
          val ptr = ((len and 0x3F) shl 8) or (p[at + 1].toInt() and 0xFF)
          if (ptr >= at) return null
          if (end < 0) end = at + 2
          if (++jumps > MAX_JUMPS) return null
          at = ptr
        }
        else -> return null
      }
    }
    return null
  }

  /** بی بزرگ/کوچکِ ASCII (§16)، برچسب به برچسب */
  private fun same(a: List<String>, b: List<String>): Boolean =
    a.size == b.size && a.indices.all { lowerAscii(a[it]) == lowerAscii(b[it]) }

  private fun lowerAscii(s: String): String = buildString { for (c in s) append(if (c in 'A'..'Z') c + 32 else c) }

  private fun u16(p: ByteArray, i: Int): Int = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)

  private fun u32(p: ByteArray, i: Int): Long =
    ((p[i].toLong() and 0xFF) shl 24) or ((p[i + 1].toLong() and 0xFF) shl 16) or
      ((p[i + 2].toLong() and 0xFF) shl 8) or (p[i + 3].toLong() and 0xFF)
}
