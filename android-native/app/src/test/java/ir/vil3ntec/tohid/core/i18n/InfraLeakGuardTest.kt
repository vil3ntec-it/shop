package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.SupportDiag
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 *  «هیچ جزئیاتِ فنی/زیرساخت در صفحه» (`docs/RULES-fa.md`، صاحب‌کار ۱۴۰۵/۰۸/۲۳): هیچ صفحه‌ای در هیچ برنامه‌ای و برای
 *  هیچ نقشی نشانیِ سرور، نامِ میزبان، گیت‌هاب، نسخهٔ Schema/سرور، راهِ API، کدِ HTTP، متنِ خامِ خطا، تنظیماتِ ورود
 *  (`.env`، شناسهٔ کلاینت، SHA-1) یا نشانی‌های آموخته/اعلام‌شدهٔ سرور را نشان نمی‌دهد. جزئیات فقط برای پشتیبانی
 *  فرستاده می‌شود (`data/SupportDiag`)، با میزبانِ پوشانده.
 *
 *  سه لایه، همان الگوی نگهبان‌های دیگر (`DataSafetyGuardTest`، `DariCoverageTest`):
 *   1. **جدول‌ها**: هر نوشتهٔ هر `Str*` ِ `I18nTables.all` در چهار زبان خوانده و با [FORBIDDEN] سنجیده می‌شود؛
 *   2. **صفحه‌ها**: کدِ `ui/` ِ برنامهٔ دکان (گوشی، کامپیوتر، آیفون، پیک) و برنامهٔ خرید نمادهایی را که دادهٔ زیرساخت را
 *      روی صفحه می‌برند ([UI_RULES]) فقط در جای مجازِ خودشان دارد؛
 *   3. **رفتار**: سادهٔ خطا (`SyncModel.reason`)، پیامِ HTTP ِ `ApiFailure` و پوشاندنِ میزبانِ گزارشِ پشتیبانی.
 */
class InfraLeakGuardTest {

  @After fun reset() {
    I18n.source = { Lang.FA }
    I18n.audit = null
  }

  /** هر یک از این‌ها در نوشتهٔ صفحه ⇒ سرخ */
  private val FORBIDDEN = listOf(
    "گیت‌هاب" to Regex("""(?i)github|گیت‌?هاب|ګیټ‌?هب"""),
    "Schema" to Regex("""(?i)\bschema\b"""),
    "نشانیِ کامل" to Regex("""(?i)\b(?:https?|wss?)://[a-z0-9\[]"""),
    "میزبانِ زیرساخت" to Regex("""(?i)\b(?:api|panel|admin|sync|ws)\.vill3n\.top\b|github\.io|localhost|127\.0\.0\.1"""),
    "تنظیماتِ ورود/سرور" to Regex("""(?i)\.env\b|_CLIENT_ID|\bSHA-?1\b|Google Cloud|docker|ایمیجِ|/api/"""),
    "کدِ HTTP" to Regex("""(?i)\bHTTP\s*[0-9]{3}\b"""),
    "راهِ API" to Regex("""(?i)\bAPI\s*path\b|راهِ API|د API لار"""),
  )

  private class Row(val where: String, val text: String)

  private fun methods(table: Any) = table.javaClass.declaredMethods.filter {
    Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.returnType == String::class.java
  }

  /** هر نوشتهٔ هر جدول در هر چهار زبان (آرگومان‌ها عددِ ساختگی) */
  private fun rows(): List<Row> {
    val out = ArrayList<Row>()
    for (table in I18nTables.all) for (m in methods(table)) {
      val args = m.parameterTypes.mapIndexed { i, type ->
        when (type) {
          Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7301 + i
          Long::class.javaPrimitiveType, Long::class.javaObjectType -> 7301L + i
          Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> true
          else -> (7301 + i).toString()
        }
      }.toTypedArray()
      for (lang in Lang.entries) {
        I18n.source = { lang }
        val text = runCatching { m.invoke(table, *args) as String }.getOrNull() ?: continue
        out += Row("${table.javaClass.simpleName}.${m.name} [${lang.code}]", text)
      }
    }
    I18n.source = { Lang.FA }
    return out
  }

  @Test fun `هیچ نوشتهٔ جدولی در هیچ زبانی نشانی، گیت‌هاب، Schema، کدِ HTTP یا تنظیماتِ سرور ندارد`() {
    val all = rows()
    assertTrue("جدولی خوانده نشد (${all.size})", all.size > 5000)
    val bad = all.flatMap { r -> FORBIDDEN.filter { it.second.containsMatchIn(r.text) }.map { "${r.where} (${it.first}): «${r.text}»" } }
    assertTrue("جزئیاتِ فنی/زیرساخت در نوشتهٔ صفحه — جملهٔ ساده بنویسید و جزئیات را به پشتیبانی بفرستید:\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  @Test fun `نوشته‌های حذف‌شدهٔ نشانی، نسخهٔ سرور، Schema و جزئیاتِ خام برنگشته‌اند`() {
    val gone = mapOf(
      StrNav::class.java to listOf("getServerVersion", "getApiPath", "schemaVersion", "serverN"),
      StrSettings::class.java to listOf("getServerAddress", "details", "diagRejectedTitle"),
    )
    for ((cls, names) in gone) for (n in names) {
      assertFalse("${cls.simpleName}.$n برگشته — جزئیاتِ زیرساخت روی صفحه", cls.declaredMethods.any { it.name == n })
    }
  }

  /* ------------------------------- صفحه‌ها ------------------------------- */

  private class Rule(val why: String, val pattern: Regex, val allowed: Set<String> = emptySet())

  /** نمادهایی که دادهٔ زیرساخت را روی صفحه می‌برند؛ `allowed` = پرونده‌هایی که آن را **نشان نمی‌دهند** (دلیل کنارش) */
  private val UI_RULES = listOf(
    //  نسخه و راهِ API ِ سرور فقط در گزارشِ پشتیبانی (`SyncModel.facts`)
    Rule("نسخه/راهِ API ِ سرور روی صفحه", Regex("""(?m)^(?![^\n]*SyncModel\.facts\()[^\n]*ServerPulse\.(?:version|prefix)\b""")),
    Rule("نشانیِ سرور روی صفحه", Regex("""ServerPulse\.address\b""")),
    //  V3GuideAndroid: نشانیِ فایلِ ویدیو برای پخش‌کننده، نه متن؛ StorefrontCampaigns: پیوندِ عمومیِ ویترین برای QR
    Rule("نشانیِ پایهٔ سرور در صفحه", Regex("""AppConfig\.baseUrl\(|BuildConfig\.API_BASE\b|Backend\.apiPrefix\("""),
      //  ServerPulse: راهِ API فقط برای `SyncModel.facts` (گزارشِ پشتیبانی)
      setOf("v3/V3GuideAndroid.kt", "screens/StorefrontCampaigns.kt", "screens/ServerPulse.kt")),
    Rule("نشانی‌های سرور (دستی/آموخته/اعلام‌شده) در فهرست", Regex("""\.urls\.forEach\s*\{[^}\n]*Text\(|Text\([^)\n]*\.urls\b""")),
    //  خطای موتور فقط از راهِ `SyncModel.reason` (جملهٔ ساده) به نوار، خانه و برگهٔ خروج می‌رسد
    Rule("خطای خامِ همگام‌سازی در نوارِ وضعیت", Regex("""SyncBadge\.line\((?![^\n]*reason\()[^\n]*\.lastError\b""")),
    Rule("خطای خامِ همگام‌سازی روی صفحه", Regex("""Text\(\s*(?:v1|sync|status|ui)\??\.lastError\b|SignOutUi\([^\n]*,\s*v1\.lastError\b""")),
    Rule("جزئیاتِ خامِ گرهٔ همگام‌سازی روی صفحه", Regex("""Text\([^\n]*\bd\.detail\b|StrSettings\.details\(""")),
    Rule("نسخهٔ Schema روی صفحه", Regex("""\.(?:schemaVersion|serverSchema)\b"""), setOf("v3/settings/sections/SyncModel.kt")),
    Rule("یادداشتِ نشانیِ سابقهٔ سرورها", Regex("""e\.note\.isNotBlank\(\)\)\s*" \("""), emptySet()),
  )

  private val repo: File get() = DesktopPhrases.repo()

  /** پوشه‌های صفحهٔ برنامهٔ دکان (گوشی/کامپیوتر/آیفون/پیک از همین کد ساخته می‌شوند) و برنامهٔ خرید */
  private fun uiFiles(): Map<String, File> {
    val shop = repo.resolve("android-native/app/src/main/java/ir/vil3ntec/tohid/ui")
    val desk = repo.resolve("desktop/src/main/kotlin/ir/vil3ntec/tohid/ui")
    val shopper = repo.resolve("customer-android/app/src/main/java/ir/vil3ntec/tohid/customer/ui")
    assertTrue("پوشهٔ صفحه‌ها پیدا نشد", shop.isDirectory && shopper.isDirectory)
    val out = LinkedHashMap<String, File>()
    for (base in listOf(shop, desk, shopper)) if (base.isDirectory) {
      base.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { out[it.relativeTo(base).invariantSeparatorsPath] = it }
    }
    return out
  }

  /** کد بی توضیح (خطِ `//`، `*` و دنبالهٔ `// …`) */
  private fun code(f: File): String = f.readLines().filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
    .joinToString("\n") { it.replace(Regex("""\s//\s.*$"""), "") }

  @Test fun `صفحه‌ها دادهٔ زیرساخت را فقط در جای مجاز و بی نمایش دارند`() {
    val files = uiFiles()
    assertTrue("پرونده‌ای پیدا نشد (${files.size})", files.size > 400)
    val bad = ArrayList<String>()
    for ((rel, f) in files) {
      val src = code(f)
      for (r in UI_RULES) if (rel !in r.allowed) r.pattern.findAll(src).forEach { bad += "$rel (${r.why}): ${it.value.take(90)}" }
    }
    assertTrue("جزئیاتِ فنی/زیرساخت روی صفحه:\n" + bad.joinToString("\n"), bad.isEmpty())
    //  جاهای مجاز هنوز هستند (فهرستِ مجاز کهنه نشود)
    for (r in UI_RULES) for (a in r.allowed) assertTrue("پروندهٔ مجازِ «$a» دیگر نیست — فهرست را کوتاه کنید", a in files)
  }

  @Test fun `برنامهٔ خرید نشانیِ سرور را فقط در کادرِ خالیِ ساختِ آزمون دارد`() {
    val f = repo.resolve("customer-android/app/src/main/java/ir/vil3ntec/tohid/customer/ui/SettingsScreen.kt")
    val src = code(f)
    assertFalse("نشانیِ ساخت/آموخته در کادرِ تنظیماتِ خریدار", src.contains("platform.server()"))
    assertTrue("کادرِ نشانی فقط با Platform.serverField", src.contains("if (platform.serverField)"))
    val gradle = repo.resolve("customer-android/gradle.properties").readText()
    assertTrue("کادرِ نشانیِ خریدار پیش‌فرض خاموش", Regex("""(?m)^tohid\.serverField=false$""").containsMatchIn(gradle))
  }

  @Test fun `«سرورها» فقط برای صاحبِ دکان در فهرست و جست‌وجو`() {
    assertEquals(setOf(ir.vil3ntec.tohid.ui.screens.SettingsLayout.SERVERS), ir.vil3ntec.tohid.ui.screens.SettingsLayout.OWNER_ONLY)
    val rows = code(repo.resolve("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/settings/SettingsRows.kt"))
    assertTrue(rows.contains("facts.owner || it !in SettingsLayout.OWNER_ONLY"))
    val index = code(repo.resolve("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/settings/SettingsIndexScreen.kt"))
    assertTrue(index.contains("facts.owner || it.key !in SettingsLayout.OWNER_ONLY"))
    assertFalse("پیش‌فرضِ «صاحب» باید خاموش باشد", ir.vil3ntec.tohid.ui.v3.settings.SettingsFacts().owner)
  }

  /* ------------------------------- رفتار ------------------------------- */

  private val technical = Regex("[A-Za-z0-9]|://")

  @Test fun `سادهٔ خطای همگام‌سازی هرگز متنِ فنی را نشان نمی‌دهد`() {
    val raws = listOf(
      "HTTP 503 Service Unavailable", "Unable to resolve host \"api.vill3n.top\": No address associated with hostname",
      "java.net.SocketTimeoutException: timeout", "خطای سرور (502)", "{\"error\":{\"code\":\"internal\",\"message\":\"x\"}}",
      "at ir.vil3ntec.tohid.sync.v1.SyncV1Engine.run(SyncV1Engine.kt:178)", "Snapshot ِ سرور cursor نداشت",
      "https://192.168.1.20:8443/api/v1/sync", "سایهٔ همگام‌سازی خراب است (part): shadow.json", "boom",
    )
    for (lang in listOf(Lang.FA, Lang.PRS, Lang.EN, Lang.PS)) {
      I18n.source = { lang }
      for (raw in raws) {
        val shown = SyncModel.reason(raw)
        assertTrue("خالی برای «$raw»", shown.isNotBlank())
        if (lang.persian) assertFalse("متنِ فنی در «$shown» (از «$raw»)", technical.containsMatchIn(shown))
        for ((why, p) in FORBIDDEN) assertFalse("$why در «$shown»", p.containsMatchIn(shown))
      }
    }
    I18n.source = { Lang.FA }
    assertEquals("", SyncModel.reason("  "))
    assertEquals("اشتراک تمام شده", SyncModel.reason("اشتراک تمام شده"))
    //  یادداشتِ «وضعیتِ سرور»: «بالا ولی ناآماده» ساده، خطای خام هرگز
    val down = SyncModel.pulseNote(ir.vil3ntec.tohid.ui.screens.ServerPulse.State.DOWN, "پاسخ سرور درست نبود (418)")
    assertFalse(technical.containsMatchIn(down.orEmpty()))
    assertEquals(null, SyncModel.pulseNote(ir.vil3ntec.tohid.ui.screens.ServerPulse.State.UP, "database: ECONNREFUSED 10.0.0.5:5432"))
  }

  @Test fun `پیامِ HTTP ِ لایهٔ شبکه بی کدِ وضعیت و بی دستورِ سرور است`() {
    for (status in listOf(302, 404, 418, 500, 503, 599)) {
      val m = ApiFailure.fromHttp(status, null, false).userMessage
      assertFalse("کدِ $status در «$m»", Regex("[0-9]").containsMatchIn(m))
    }
    val old = ApiFailure.fromHttp(404, """{"error":"not_found"}""", true).userMessage
    assertFalse("دستورِ سرور در «$old»", Regex("""نسخهٔ تازهٔ سرور|بالا بیاورید|docker""").containsMatchIn(old))
    assertEquals("پاسخ سرور خوانده نشد", ApiFailure.InvalidResponse("dispute").userMessage)
  }

  @Test fun `گزارشِ پشتیبانی میزبان را می‌پوشاند و همان گره را پشتِ هم نمی‌فرستد`() {
    val masked = SupportDiag.maskHosts(
      "Unable to resolve host \"api.vill3n.top\" · https://api.vill3n.top/api/v1/sync · wss://ws.vill3n.top:443 · 192.168.1.20:8443 · shop.vill3n.top",
    )
    assertFalse(masked, Regex("""vill3n|192\.168""").containsMatchIn(masked))
    assertTrue(masked, masked.contains("api.${SupportDiag.MASK}"))
    assertTrue(masked, masked.contains("https://api.${SupportDiag.MASK}/api/v1/sync"))
    //  نامِ کلاس و پیامِ دیگر دست نمی‌خورد (پشتیبانی همان را لازم دارد)
    assertEquals("java.net.SocketTimeoutException: timeout", SupportDiag.maskHosts("java.net.SocketTimeoutException: timeout"))
    val r = SupportDiag.report(
      "OFFLINE", "Unable to resolve host \"api.vill3n.top\" for a@b.co",
      linkedMapOf("schema" to "4", "serverSchema" to "3", "apiPrefix" to "/api/v1", "empty" to ""), "2.14.5", 1_000_000L,
    )
    assertTrue(r.shown)   //  برگهٔ «این کد را به پشتیبانی بگویید» ِ خطای برنامه برایش باز نمی‌شود
    assertFalse(r.sent)
    assertFalse(r.message, r.message.contains("vill3n") || r.message.contains("a@b.co"))
    assertTrue(r.message.startsWith("diag:OFFLINE"))
    assertTrue(r.stack, r.stack.contains("schema=4") && r.stack.contains("serverSchema=3") && !r.stack.contains("empty="))
    //  همان گره (فقط عددِ دیگر) همان اثر
    assertEquals(SupportDiag.signature("SERVER", "خطای سرور (502)"), SupportDiag.signature("SERVER", "خطای سرور (503)"))
    assertFalse(SupportDiag.signature("SERVER", "x") == SupportDiag.signature("LOCAL", "x"))
    val sig = r.signature
    assertTrue(SupportDiag.due(null, 0L, sig, 1_000L))
    assertFalse(SupportDiag.due(sig, 1_000L, sig, 1_000L + 60_000L))
    assertTrue(SupportDiag.due(sig, 1_000L, sig, 1_000L + SupportDiag.REPEAT_MS))
    assertTrue(SupportDiag.due("other", 1_000L, sig, 2_000L))
    assertTrue("ساعتِ عقب‌رفته ⇒ دوباره", SupportDiag.due(sig, 10_000L, sig, 5_000L))
  }
}
