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

  // ── OPS-01: انتشارِ مرحله‌ای با برچسبِ نسخه‌دار ──────────────────────

  @Test
  fun `برنامه به سرور می‌گوید برچسبِ نسخه‌دار را می‌شناسد`() {
    assertEquals(
      "https://api.example/api/releases/tohid-native/latest?shop=shopA&device=dev1&staged=1",
      ReleaseFeed.latestUrl("https://api.example/", "tohid-native", "shopA", "dev1"),
    )
    //  شناسهٔ ناجور فرستاده نمی‌شود؛ staged همیشه
    assertEquals(
      "https://api.example/api/releases/tohid-native/latest?staged=1",
      ReleaseFeed.latestUrl("https://api.example", "tohid-native", " ", "a b"),
    )
  }

  @Test
  fun `برچسبِ نسخه‌دارِ همان نسخه پذیرفته می‌شود و فایل‌ها از همان برچسب می‌آیند`() {
    val r = ReleaseFeed.parseLatest(
      """{"tag":"tohid-native-v3.2.70","channel":"staged","version":"3.2.70","notes":"s",
         "assets":[{"name":"SHA256SUMS.txt","size":1},{"name":"Tohid-Native-3.2.70.apk","size":9}]}""",
      "tohid-native",
    )!!
    assertEquals("tohid-native-v3.2.70", r.tag)
    assertTrue(r.hasSums)
    assertEquals(
      "https://api.example/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk",
      ReleaseFeed.fileUrl("https://api.example", r.tag, ReleaseFeed.androidInstaller(r.assets)!!.name),
    )
  }

  @Test
  fun `برچسبِ نسخه‌دارِ نسخهٔ دیگر یا پایهٔ دیگر پذیرفته نمی‌شود`() {
    val body = { tag: String, v: String -> """{"tag":"$tag","version":"$v","assets":[{"name":"Tohid-Native-$v.apk","size":5}]}""" }
    assertNull(ReleaseFeed.parseLatest(body("tohid-native-v3.2.71", "3.2.70"), "tohid-native"))
    assertNull(ReleaseFeed.parseLatest(body("tohid-desktop-v3.2.70", "3.2.70"), "tohid-native"))
    assertNull(ReleaseFeed.parseLatest(body("tohid-native-vx", "3.2.70"), "tohid-native"))
    assertNull(ReleaseFeed.parseLatest(body("tohid-native-v", "3.2.70"), "tohid-native"))
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
      "https://api.example/api/releases/tohid-native/latest?shop=shop_ab12&device=0f9e&staged=1",
      ReleaseFeed.latestUrl("https://api.example/", "tohid-native", "shop_ab12", "0f9e"),
    )
    assertEquals(
      "https://api.example/api/releases/tohid-desktop/latest?staged=1",
      ReleaseFeed.latestUrl("https://api.example", "tohid-desktop", "", ""),
    )
    //  شناسهٔ خراب هرگز پرسش را نمی‌شکند
    assertEquals(
      "https://api.example/api/releases/tohid-native/latest?device=0f9e&staged=1",
      ReleaseFeed.latestUrl("https://api.example", "tohid-native", "a&b=c", "0f9e"),
    )
  }

  // ── پیش‌نمایش ─────────────────────────────────────────────────────

  private fun preview(tag: String = "tohid-native-preview", label: String = "3.2.17-preview", sums: Boolean = true) =
    """{"tag_name":"$tag","draft":false,"body":"پیش","assets":[""" +
      """{"name":"tohid-preview.apk.preview","label":"$label","size":5000000,"browser_download_url":"https://dl.example/tohid-preview.apk.preview"},""" +
      """{"name":"tohid-preview.zip","size":4900000,"browser_download_url":"https://dl.example/tohid-preview.zip"}""" +
      (if (sums) "," + asset("SHA256SUMS.txt") else "") + "]}"

  @Test
  fun `پیش‌نمایش فقط از برچسبِ خودش و با نسخهٔ برچسبِ دارایی`() {
    val p = ReleaseFeed.previewPick(preview())!!
    assertEquals("3.2.17-preview", p.version)
    val apk = p.assets.first { it.name == ReleaseFeed.PREVIEW_ASSET }
    assertEquals("tohid-preview.apk.preview", apk.name)
    assertEquals("https://dl.example/tohid-preview.apk.preview", apk.url)
    assertEquals(5000000L, apk.size)
    assertTrue(p.hasSums)
    assertNull(ReleaseFeed.previewPick(preview(tag = "tohid-native")))
    assertNull(ReleaseFeed.previewPick(preview(tag = "tohid-native-beta")))
    assertNull(ReleaseFeed.previewPick(preview(label = "")))
    assertNull(ReleaseFeed.previewPick(preview(sums = false)))
    //  پایدار هرگز پیش‌نمایش را برنمی‌دارد: نصبی‌اش به `.apk` ختم نمی‌شود
    assertNull(ReleaseFeed.githubPick(preview(tag = "tohid-native"), "tohid-native"))
  }

  @Test
  fun `شمارهٔ ساختِ پیش‌نمایش عددی سنجیده می‌شود`() {
    assertEquals(17, ReleaseFeed.previewBuild("3.2.17-preview"))
    assertTrue(ReleaseFeed.previewBuild("3.3.10-preview") > ReleaseFeed.previewBuild("3.2.9-preview"))
    assertEquals(-1, ReleaseFeed.previewBuild("3.2.17"))
    assertEquals(-1, ReleaseFeed.previewBuild(""))
  }
}
