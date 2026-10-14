package ir.vil3ntec.tohid.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 *  خواندنِ پاسخِ انتشارها — جدا از شبکه تا آزموده شود (`ReleaseFeedTest`).
 *  هم `Updater`ِ اندروید و هم `Updater`ِ کامپیوتر (`desktop/…/UpdaterDesk.kt`)
 *  از همین استفاده می‌کنند.
 *
 *  ── کانالِ آزمایشی (F0-27) ────────────────────────────────────────
 *  برنامه به سرورِ خودش `?shop=&device=` می‌فرستد. اگر دکان یا دستگاه در
 *  فهرستِ آزمایشیِ `live_config` (scope `release`) باشد، سرور از پایدار و
 *  آزمایشی هر کدام بالاتر است را می‌دهد و `tag` می‌گوید فایل‌ها زیرِ کدام
 *  برچسب‌اند. نصبیِ اندرویدِ آزمایشی به `.apk.beta` ختم می‌شود، نه `.apk`،
 *  تا نسخه‌های قدیمی که از GitHub می‌پرسند برش ندارند.
 *    ⛔ جز `<برچسب>` و `<برچسب>-beta` هیچ برچسبی پذیرفته نمی‌شود.
 *    ⛔ سرورِ قدیمی که `tag` نمی‌دهد ⇒ همان برچسبِ پایدار، مثلِ پیش.
 *  ──────────────────────────────────────────────────────────────────
 */
object ReleaseFeed {

  data class Asset(val name: String, val size: Long, val url: String = "")

  /** پاسخِ `/api/releases/<برچسب>/latest` */
  data class Latest(val tag: String, val version: String, val notes: String, val assets: List<Asset>) {
    val hasSums: Boolean get() = assets.any { it.name.equals(SUMS, true) }
  }

  /** انتخاب از api.github.com */
  data class GitHubPick(val notes: String, val apk: Asset, val sumsUrl: String)

  const val SUMS = "SHA256SUMS.txt"
  private val VERSION = Regex("^\\d+(\\.\\d+)+$")
  private val NUMBERED = Regex("\\d+(\\.\\d+)+")
  /** همان قاعدهٔ شناسه در `server/src/routes/releases.js` — پس رمزگذاری لازم نیست */
  private val ID = Regex("^[A-Za-z0-9_-]{1,80}$")

  private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

  /** `<سرور>/api/releases/<برچسب>/latest` — شناسهٔ خالی یا نادرست فرستاده نمی‌شود */
  fun latestUrl(server: String, tag: String, shop: String, device: String): String {
    val q = listOfNotNull(
      shop.trim().takeIf { ID.matches(it) }?.let { "shop=$it" },
      device.trim().takeIf { ID.matches(it) }?.let { "device=$it" },
    )
    val base = "${server.trimEnd('/')}/api/releases/$tag/latest"
    return if (q.isEmpty()) base else base + "?" + q.joinToString("&")
  }

  /** نشانیِ یک فایلِ همان انتشار روی سرورِ خودمان */
  fun fileUrl(server: String, tag: String, name: String): String =
    "${server.trimEnd('/')}/api/releases/$tag/file/" + URLEncoder.encode(name, "UTF-8")

  /** پاسخِ سرور؛ null یعنی به‌کار نمی‌آید (و به‌روزرسان سراغِ راهِ بعدی می‌رود) */
  fun parseLatest(body: String, requested: String): Latest? {
    val r = JSONObject(body)
    val tag = str(r, "tag").ifBlank { requested }
    if (tag != requested && tag != "$requested-beta") return null
    val version = str(r, "version")
    if (!VERSION.matches(version)) return null
    val list = r.optJSONArray("assets") ?: return null
    val assets = (0 until list.length()).map { list.getJSONObject(it) }
      .map { Asset(it.getString("name"), it.optLong("size")) }
    return Latest(tag, version, str(r, "notes"), assets)
  }

  /**
   *  نصبیِ اندروید: نسخه‌دار (`Tohid-Native-3.2.45.apk` یا `….apk.beta`) بر
   *  نامِ ثابتِ بی‌شماره (`Tohid-Native.apk`) ترجیح دارد.
   */
  fun androidInstaller(assets: List<Asset>): Asset? {
    val apks = assets.filter { it.name.endsWith(".apk", true) || it.name.endsWith(".apk.beta", true) }
    return apks.firstOrNull { NUMBERED.containsMatchIn(it.name) } ?: apks.firstOrNull()
  }

  /**
   *  پاسخِ api.github.com — یک انتشار (`/releases/tags/<برچسب>`) یا فهرست.
   *
   *  ⛔ فقط انتشاری که `tag_name`ش **همین** برچسب است. پیش از این، اولین
   *  انتشاری که هر `.apk`ی داشت برداشته می‌شد: برنامهٔ مدیریت (`tohid-admin`)
   *  یا یک `v*`ِ قدیمی. آزمایشی (`….apk.beta`) از این راه هرگز برداشته نمی‌شود.
   */
  fun githubPick(body: String, tag: String): GitHubPick? {
    val text = body.trimStart()
    val list = if (text.startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text))
    for (i in 0 until list.length()) {
      val r = list.getJSONObject(i)
      if (r.optBoolean("draft") || str(r, "tag_name") != tag) continue
      val raw = r.optJSONArray("assets") ?: continue
      val assets = (0 until raw.length()).map { raw.getJSONObject(it) }
        .map { Asset(it.getString("name"), it.optLong("size"), str(it, "browser_download_url")) }
      val apks = assets.filter { it.name.endsWith(".apk", true) }
      val apk = apks.firstOrNull { NUMBERED.containsMatchIn(it.name) } ?: apks.firstOrNull() ?: continue
      val sums = assets.firstOrNull { it.name.equals(SUMS, true) }?.url.orEmpty()
      return GitHubPick(str(r, "body"), apk, sums)
    }
    return null
  }
}
