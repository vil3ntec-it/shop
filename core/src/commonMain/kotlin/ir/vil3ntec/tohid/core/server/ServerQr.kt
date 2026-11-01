package ir.vil3ntec.tohid.core.server

/**
 *  کیو‌آرِ «افزودنِ سرور» — `tohid-server:` + JSON.
 *
 *  ```
 *  tohid-server:{"v":1,"url":"https://shop.example.com","alt":["https://192.168.1.10:3443"],
 *                "serverId":"srv_…","name":"دکانِ مرکزی","certs":{"https://192.168.1.10:3443":"<sha256>"}}
 *  ```
 *
 *  این‌جا تعریف شده تا پنلِ مدیریتِ سرورها (کارِ بعدی) همین را بسازد و برنامه همین را بخواند. کیو‌آر
 *  شناسهٔ سرور را **همراه دارد**: پس برنامه پس از وصل، همان را با `config/server` می‌سنجد و
 *  کیو‌آری که به سرورِ دیگری اشاره کند (نشانیِ درست، شناسهٔ دیگر) همان‌جا بسته می‌شود.
 *  کیو‌آر هیچ راز و هیچ توکنی ندارد.
 */
object ServerQr {

  const val PREFIX: String = "tohid-server:"
  const val VERSION: Int = 1
  const val MAX_LENGTH: Int = 2048

  /** آنچه از کیو‌آر خوانده شد */
  data class Payload(
    val urls: List<String>,
    val serverId: String,
    val name: String,
    val certPins: Map<String, String>,
  )

  sealed class Read {
    data class Ok(val payload: Payload) : Read()
    data class Bad(val message: String) : Read()
    /** اصلاً کیو‌آرِ سرور نیست (مثلاً یک نشانیِ ساده) */
    object NotServerQr : Read()
  }

  fun isServerQr(text: String?): Boolean = text?.trim()?.startsWith(PREFIX) == true

  fun encode(p: Payload): String {
    val urls = p.urls.map(ServerAddress::normalize).filter { it.isNotEmpty() }.distinct()
    require(urls.isNotEmpty()) { "url" }
    require(ServerInfo.validServerId(p.serverId)) { "serverId" }
    val body = linkedMapOf<String, Any?>(
      "v" to VERSION,
      "url" to urls.first(),
      "serverId" to p.serverId,
      "name" to ServerInfo.cleanName(p.name),
    )
    if (urls.size > 1) body["alt"] = urls.drop(1)
    val certs = p.certPins.mapNotNull { (u, pin) ->
      val nu = ServerAddress.normalize(u)
      val np = ServerProfile.normalizeCertPin(pin)
      if (nu in urls && np != null) nu to np else null
    }.toMap()
    if (certs.isNotEmpty()) body["certs"] = certs
    return PREFIX + MiniJson.encode(body)
  }

  fun decode(text: String?): Read {
    val t = text?.trim() ?: return Read.NotServerQr
    if (!t.startsWith(PREFIX)) return Read.NotServerQr
    if (t.length > MAX_LENGTH) return Read.Bad("کیو‌آر بیش از اندازه بزرگ است")
    val o = MiniJson.obj(t.substring(PREFIX.length)) ?: return Read.Bad("کیو‌آرِ سرور خوانا نیست")
    val v = (o["v"] as? Long)?.toInt() ?: return Read.Bad("کیو‌آرِ سرور نسخه ندارد")
    if (v > VERSION) return Read.Bad("این کیو‌آر از نسخهٔ تازه‌ترِ برنامه است؛ برنامه را به‌روز کنید")
    if (v < 1) return Read.Bad("کیو‌آرِ سرور خوانا نیست")
    val primary = (o["url"] as? String)?.let { ServerAddress.parse(it)?.url }
      ?: return Read.Bad("نشانیِ سرور در کیو‌آر درست نیست")
    val alt = (o["alt"] as? List<*>).orEmpty()
    if (alt.size > ServerProfiles.MAX_URLS - 1) return Read.Bad("کیو‌آر بیش از اندازه نشانی دارد")
    val alternates = alt.map { (it as? String)?.let { s -> ServerAddress.parse(s)?.url } ?: return Read.Bad("نشانیِ دومِ سرور در کیو‌آر درست نیست") }
    val serverId = (o["serverId"] as? String)?.takeIf { ServerInfo.validServerId(it) }
      ?: return Read.Bad("شناسهٔ سرور در کیو‌آر نیست یا درست نیست")
    val urls = (listOf(primary) + alternates).distinct()
    val certs = (o["certs"] as? Map<*, *>).orEmpty().map { (k, value) ->
      val u = (k as? String)?.let { ServerAddress.parse(it)?.url }
      val pin = ServerProfile.normalizeCertPin(value as? String)
      if (u == null || pin == null || u !in urls) return Read.Bad("اثرِ انگشتِ گواهی در کیو‌آر درست نیست")
      u to pin
    }.toMap()
    return Read.Ok(Payload(urls, serverId, ServerInfo.cleanName(o["name"] as? String).ifEmpty { "سرور" }, certs))
  }
}
