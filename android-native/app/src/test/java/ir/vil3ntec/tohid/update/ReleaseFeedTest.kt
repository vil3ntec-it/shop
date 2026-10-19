package ir.vil3ntec.tohid.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-27 — کدام انتشار و کدام فایل.
 *
 *  ریشهٔ اشکالِ راهِ GitHub: از ده انتشارِ تازه، **اولین** انتشاری که هر
 *  `.apk`ی داشت برداشته می‌شد و `tag_name` نگاه نمی‌شد — پس برنامهٔ مدیریت
 *  (`tohid-admin`) یا یک `v*`ِ قدیمی می‌توانست به‌جای نسخهٔ بومی پیشنهاد شود.
 */
class ReleaseFeedTest {

  private fun asset(name: String, size: Long = 100) =
    """{"name":"$name","size":$size,"browser_download_url":"https://dl.example/$name"}"""

  private fun release(tag: String, vararg assets: String, draft: Boolean = false, body: String = "یادداشت") =
    """{"tag_name":"$tag","draft":$draft,"body":"$body","assets":[${assets.joinToString(",") { asset(it) }}]}"""

  // ── GitHub ─────────────────────────────────────────────────────────

  @Test
  fun `برنامهٔ مدیریت و انتشارِ قدیمی پیش از نسخهٔ بومی برداشته نمی‌شوند`() {
    //  همان ترتیبی که api.github.com می‌دهد: تازه‌ترین اول
    val list = "[" + listOf(
      release("tohid-admin", "Tohid-Admin-1.0.55.apk", "Tohid-Admin.apk"),
      release("v1.4.0", "app-release.apk"),
      release("tohid-native", "Tohid-Native.apk", "Tohid-Native-3.2.45.apk", "SHA256SUMS.txt", body = "تازه"),
    ).joinToString(",") + "]"

    val pick = ReleaseFeed.githubPick(list, "tohid-native")!!
    assertEquals("Tohid-Native-3.2.45.apk", pick.apk.name)
    assertEquals("https://dl.example/Tohid-Native-3.2.45.apk", pick.apk.url)
    assertEquals("https://dl.example/SHA256SUMS.txt", pick.sumsUrl)
    assertEquals("تازه", pick.notes)
  }

  @Test
  fun `انتشارِ تکیِ برچسبِ دیگر پذیرفته نمی‌شود`() {
    assertNull(ReleaseFeed.githubPick(release("tohid-admin", "Tohid-Admin-1.0.55.apk"), "tohid-native"))
  }

  @Test
  fun `انتشارِ تکیِ همین برچسب`() {
    val pick = ReleaseFeed.githubPick(release("tohid-native", "Tohid-Native-3.2.45.apk"), "tohid-native")!!
    assertEquals("Tohid-Native-3.2.45.apk", pick.apk.name)
    assertEquals("", pick.sumsUrl)
  }

  @Test
  fun `پیش‌نویس و آزمایشی از راهِ GitHub برداشته نمی‌شوند`() {
    val list = "[" + listOf(
      release("tohid-native", "Tohid-Native-3.2.46.apk", draft = true),
      release("tohid-native-beta", "Tohid-Native-3.2.50.apk.beta", "SHA256SUMS.txt"),
    ).joinToString(",") + "]"
    assertNull(ReleaseFeed.githubPick(list, "tohid-native"))
  }

  // ── سرورِ خودمان ───────────────────────────────────────────────────

  @Test
  fun `سرورِ قدیمی که tag نمی‌دهد یعنی همان برچسبِ پایدار`() {
    val r = ReleaseFeed.parseLatest(
      """{"version":"3.2.45","notes":"n","assets":[{"name":"Tohid-Native-3.2.45.apk","size":5}]}""",
      "tohid-native",
    )!!
    assertEquals("tohid-native", r.tag)
    assertEquals("3.2.45", r.version)
    assertEquals(false, r.hasSums)
  }

  @Test
  fun `سرورِ تازه کانالِ آزمایشی را با tag می‌گوید و فایل‌ها از همان برچسب می‌آیند`() {
    val r = ReleaseFeed.parseLatest(
      """{"tag":"tohid-native-beta","channel":"beta","version":"3.2.50","notes":"b",
         "assets":[{"name":"SHA256SUMS.txt","size":1},{"name":"Tohid-Native-3.2.50.apk.beta","size":9}]}""",
      "tohid-native",
    )!!
    assertEquals("tohid-native-beta", r.tag)
    assertTrue(r.hasSums)
    val apk = ReleaseFeed.androidInstaller(r.assets)!!
    assertEquals("Tohid-Native-3.2.50.apk.beta", apk.name)
    assertEquals(9L, apk.size)
    assertEquals(
      "https://api.example/api/releases/tohid-native-beta/file/Tohid-Native-3.2.50.apk.beta",
      ReleaseFeed.fileUrl("https://api.example/", r.tag, apk.name),
    )
  }

  @Test
  fun `برچسبِ بیگانه از سرور پذیرفته نمی‌شود`() {
    assertNull(ReleaseFeed.parseLatest(
      """{"tag":"tohid-admin","version":"1.0.55","assets":[{"name":"Tohid-Admin-1.0.55.apk","size":5}]}""",
      "tohid-native",
    ))
  }

  // ── §۳۸.۹: حداقل نسخهٔ پشتیبانی‌شده ────────────────────────────────

  @Test
  fun `حداقل نسخه از سرور خوانده می‌شود؛ نبود یا نادرست یعنی حداقلی نیست`() {
    val body = { min: String -> """{"tag":"tohid-native","version":"3.2.50",$min"assets":[{"name":"Tohid-Native-3.2.50.apk","size":5}]}""" }
    assertEquals("3.2.45", ReleaseFeed.parseLatest(body(""""minVersion":"3.2.45","""), "tohid-native")!!.minVersion)
    //  سرورِ قدیمی که minVersion نمی‌دهد
    assertEquals("", ReleaseFeed.parseLatest(body(""), "tohid-native")!!.minVersion)
    assertEquals("", ReleaseFeed.parseLatest(body(""""minVersion":"","""), "tohid-native")!!.minVersion)
    assertEquals("", ReleaseFeed.parseLatest(body(""""minVersion":"سه","""), "tohid-native")!!.minVersion)
    assertEquals("", ReleaseFeed.parseLatest(body(""""minVersion":null,"""), "tohid-native")!!.minVersion)
  }

  @Test
  fun `زیرِ حداقل بودن عددی سنجیده می‌شود و هرگز از روی حدس نیست`() {
    assertTrue(ReleaseFeed.belowMinimum("2.9.0", "2.10.0"))
    assertTrue(ReleaseFeed.belowMinimum("2.14.1", "2.14.2"))
    assertTrue(ReleaseFeed.belowMinimum("v2.14", "2.14.1"))
    assertTrue(ReleaseFeed.belowMinimum("2.13.9-debug", "2.14"))
    assertEquals(false, ReleaseFeed.belowMinimum("2.14.2", "2.14.2"))
    assertEquals(false, ReleaseFeed.belowMinimum("2.14.0", "2.14"))
    assertEquals(false, ReleaseFeed.belowMinimum("2.15.0", "2.14.2"))
    //  حداقلِ خالی یا نادرست، یا نسخهٔ ناخوانا ⇒ هیچ پیامِ «به‌روز کنید»ی نیست
    assertEquals(false, ReleaseFeed.belowMinimum("2.0.0", ""))
    assertEquals(false, ReleaseFeed.belowMinimum("2.0.0", "سه"))
    assertEquals(false, ReleaseFeed.belowMinimum("dev", "2.14.2"))
    assertEquals(false, ReleaseFeed.belowMinimum("", "2.14.2"))
  }

  @Test
  fun `شمارهٔ نادرست یا بی فهرستِ فایل یعنی به‌کار نمی‌آید`() {
    assertNull(ReleaseFeed.parseLatest("""{"tag":"tohid-native","version":"tohid-native","assets":[]}""", "tohid-native"))
    assertNull(ReleaseFeed.parseLatest("""{"tag":"tohid-native","version":"3.2.45"}""", "tohid-native"))
  }

  @Test
  fun `نصبیِ نسخه‌دار بر نامِ ثابت ترجیح دارد`() {
    val assets = listOf(
      ReleaseFeed.Asset("Tohid-Native.apk", 1),
      ReleaseFeed.Asset("SHA256SUMS.txt", 1),
      ReleaseFeed.Asset("Tohid-Native-3.2.45.apk", 2),
    )
    assertEquals("Tohid-Native-3.2.45.apk", ReleaseFeed.androidInstaller(assets)!!.name)
    assertEquals("Tohid-Native.apk", ReleaseFeed.androidInstaller(assets.take(2))!!.name)
    assertNull(ReleaseFeed.androidInstaller(listOf(ReleaseFeed.Asset("version.txt", 1))))
  }

  @Test
  fun `شناسهٔ دکان و دستگاه فقط وقتی هست و درست است فرستاده می‌شود`() {
    assertEquals(
      "https://api.example/api/releases/tohid-native/latest?shop=shop_ab12&device=0f9e",
      ReleaseFeed.latestUrl("https://api.example/", "tohid-native", "shop_ab12", "0f9e"),
    )
    assertEquals(
      "https://api.example/api/releases/tohid-desktop/latest",
      ReleaseFeed.latestUrl("https://api.example", "tohid-desktop", "", ""),
    )
    //  شناسهٔ خراب هرگز پرسش را نمی‌شکند
    assertEquals(
      "https://api.example/api/releases/tohid-native/latest?device=0f9e",
      ReleaseFeed.latestUrl("https://api.example", "tohid-native", "a&b=c", "0f9e"),
    )
  }
}
