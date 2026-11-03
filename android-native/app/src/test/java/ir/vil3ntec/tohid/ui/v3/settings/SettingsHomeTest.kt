package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex
import ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex.Kind
import ir.vil3ntec.tohid.ui.screens.SettingsTiers
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  تنظیماتِ نسخهٔ دوم (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۱۱): صفحهٔ اول = جست‌وجو + شش دسته (+ پیوندِ کوچکِ «قابلیت‌ها»)،
 *  هر دسته چند بخشِ کوتاه (هر بخش حداکثر ۸ ردیف)، هیچ سرخطِ تکراری، هیچ ردیفِ آزمایشی، و هیچ صفحه‌ای بی‌راه:
 *  هر بخش یا در دسته‌اش دیده می‌شود یا جست‌وجو پیدایش می‌کند.
 */
class SettingsHomeTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private fun src(rel: String): String = listOf(
    "src/main/java/ir/vil3ntec/tohid/$rel",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/$rel",
  ).map { File(it) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val allKeys: List<String> get() = SettingsLayout.GROUPS.flatMap { it.sections }
  private val everyFeature: Set<String> get() = SettingsLayout.FEATURES.toSet()

  /** ردیفِ ماژول‌ها در هر بخشِ کوتاه (بیشینه: همه روشن) */
  private fun extrasIn(group: String, part: String): Int {
    val parts = SettingsLayout.GROUPS.first { it.title == group }.parts
    return V3Modules.ALL.filter { it.id != "example" }.flatMap { it.settings }
      .count { it.group == group && (it.part ?: parts.last().id) == part }
  }

  @Test
  fun `صفحهٔ اول فقط جست‌وجو و شش دسته است — بی «پیشرفته» و بی فهرستِ بلند`() {
    assertEquals(
      listOf(SettingsLayout.SHOP, SettingsLayout.SALES, SettingsLayout.GOODS, SettingsLayout.DEVICES, SettingsLayout.DATA, SettingsLayout.LOOK),
      SettingsLayout.GROUPS.map { it.title },
    )
    val index = src("ui/v3/settings/SettingsIndexScreen.kt")
    val home = index.substringAfter("internal fun RouteScope.SettingsIndexScreen(").substringBefore("\n}\n")
    assertTrue(home.contains("SettingsSearchBox(query)"))
    assertTrue(home.contains("SettingsHomeCard(selected = null)"))
    assertTrue(home.contains("FeaturesLink"))
    //  هیچ ردیفِ بخشی در صفحهٔ اول نیست: فقط دسته‌ها (یا نتیجه‌های جست‌وجو)
    assertFalse(home.contains("SettingsRow("))
    assertFalse(home.contains("PartCard("))
    val card = index.substringAfter("internal fun SettingsHomeCard(").substringBefore("\n}\n")
    assertTrue(card.contains("SettingsLayout.GROUPS.forEachIndexed"))
    //  «پیشرفته» و کارتِ گروهِ دوباره هیچ‌جای نسخهٔ ۳ نیست
    listOf("SettingsIndexScreen.kt", "DeskSettingsScreen.kt", "SettingsRows.kt").map { src("ui/v3/settings/$it") }.forEach {
      assertFalse(it.contains("AdvancedRow"))
      assertFalse(it.contains("StrSettingsHelp.advanced"))
      assertFalse(it.contains("SettingsGroupCard"))
    }
    //  کامپیوتر همان صفحهٔ اول را در ستونِ کناری دارد
    val desk = src("ui/v3/settings/DeskSettingsScreen.kt")
    assertTrue(desk.contains("SettingsSearchBox(query)") && desk.contains("SettingsHomeCard(selected = cat)") && desk.contains("FeaturesLink"))
    //  هر دسته نام و یک خطِ ساده به هر چهار زبان دارد؛ نامِ دری همانی است که صاحب‌کار خواست
    for (lang in Lang.entries) {
      I18n.source = { lang }
      SettingsLayout.GROUPS.forEach { g ->
        assertTrue("${g.title} ($lang)", SettingsLayout.groupLabel(g.title).isNotBlank())
        assertTrue("${g.title} ($lang)", !SettingsLayout.groupLine(g.title).isNullOrBlank())
      }
    }
    I18n.source = { Lang.PRS }
    assertEquals("کالا و گدام", SettingsLayout.groupLabel(SettingsLayout.GOODS))
  }

  @Test
  fun `هیچ سرخطی تکرار نمی‌شود و هر بخشِ کوتاه حداکثر هشت ردیف دارد`() {
    for (lang in Lang.entries) {
      I18n.source = { lang }
      //  دسته‌ای که صفحهٔ خودش را ندارد («دستگاه‌ها» ⇐ همهٔ دستگاه‌ها) سرخطِ بخش هم ندارد
      val pages = SettingsLayout.GROUPS.filterNot { it.title in SettingsLayout.GROUP_LINKS }
      val heads = SettingsLayout.GROUPS.map { SettingsLayout.groupLabel(it.title) } +
        pages.flatMap { g -> g.parts.map { p -> SettingsLayout.partLabel(p.id) ?: error("${p.id} سرخط ندارد") } }
      assertEquals("سرخطِ تکراری ($lang): $heads", heads.size, heads.toSet().size)
    }
    I18n.source = { Lang.FA }
    var before = 0
    var after = 0
    SettingsLayout.GROUPS.forEach { g ->
      g.parts.forEach { p ->
        //  بدترین حال: همهٔ قابلیت‌ها روشن، همهٔ پرچم‌های سرور روشن، گوشی و کامپیوتر
        for (desktop in listOf(true, false)) {
          val n = p.rows.count { SettingsLayout.visible(it, desktop, SettingsLayout.SERVER_GATED, everyFeature) } + extrasIn(g.title, p.id)
          assertTrue("${g.title} · ${p.id}: $n ردیف", n <= 8)
        }
      }
      val phone = SettingsLayout.visibleParts(g.title, false, emptySet(), emptySet())
      phone.forEach { assertTrue(it.id, it.rows.isNotEmpty()) }
      after += phone.sumOf { it.rows.size }
    }
    //  پیش از نسخهٔ دوم (گوشی): ۱۲ ردیفِ اصلی + «پیشرفته» با هر بخشِ B ِ دیدنی و سرخط‌های تکراری
    before = allKeys.count {
      SettingsTiers.tier(it) != SettingsTiers.Tier.HIDDEN && it !in SettingsLayout.PHONE_HIDDEN && it !in SettingsLayout.SERVER_GATED &&
        it != SettingsLayout.PROFILE
    }
    println("settings v2 rows (phone, defaults): home 6 categories; category pages $after rows; before: one list of $before rows")
    assertTrue(after < before)
  }

  @Test
  fun `هیچ ردیفِ آزمایشی دیده نمی‌شود ولی جست‌وجو با برچسب پیدایش می‌کند`() {
    assertEquals(
      setOf(SettingsLayout.ANOMALY, SettingsLayout.SMART_FORECAST, SettingsLayout.STOCK_MODE, SettingsLayout.EASY_SETUP),
      SettingsLayout.EXPERIMENTAL,
    )
    //  هر بخشی که نام یا خطش «آزمایشی» می‌گوید در همین فهرست است
    for (lang in listOf(Lang.FA, Lang.EN, Lang.PS)) {
      I18n.source = { lang }
      val word = mapOf(Lang.FA to "آزمایشی", Lang.EN to "experimental", Lang.PS to "ازمایښتي").getValue(lang)
      allKeys.filter { k -> listOfNotNull(SettingsLayout.labelOf(k), SettingsHelp.line(k)).any { it.contains(word, ignoreCase = true) } }
        .forEach { assertTrue("$it ($lang) آزمایشی است ولی پنهان نیست", it in SettingsLayout.EXPERIMENTAL) }
    }
    I18n.source = { Lang.FA }
    SettingsLayout.EXPERIMENTAL.forEach { key ->
      for (desktop in listOf(true, false)) {
        assertFalse(key, SettingsLayout.visible(key, desktop, SettingsLayout.SERVER_GATED, everyFeature))
      }
      //  پیش‌فرض‌ها دست نخورده‌اند (`SettingsTiersTest.B_PREFS`) و صفحه سرِ جایش است
      assertNotNull(key, SettingsPages.of(key))
      assertEquals(SettingsTiers.Tier.ADVANCED, SettingsTiers.tier(key))
    }
    val docs = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet())
    val hits = SettingsSearchIndex.search(docs, "آزمایشی")
    assertTrue(hits.isNotEmpty())
    assertTrue(hits.filter { it.kind == Kind.SECTION }.all { it.experimental == (it.key in SettingsLayout.EXPERIMENTAL) })
    //  آزمایشی‌ها همیشه ته نتیجه‌ها
    val first = SettingsSearchIndex.search(docs, "پیش‌بینی")
    assertEquals(SettingsLayout.SMART_FORECAST, first.first { it.experimental }.key)
    assertTrue(first.dropWhile { !it.experimental }.all { it.experimental })
  }

  @Test
  fun `هر صفحه و هر نوشتنِ تنظیم راه دارد — در دسته‌اش یا با جست‌وجو`() {
    val table = V3Modules.table
    for (desktop in listOf(false, true)) {
      val docs = SettingsSearchIndex.docs(desktop, SettingsLayout.SERVER_GATED)
      val hidden = if (desktop) SettingsLayout.DESK_HIDDEN else SettingsLayout.PHONE_HIDDEN
      allKeys.filter { it !in hidden }.forEach { key ->
        if (!SettingsLayout.isLink(key)) assertNotNull("$key صفحه ندارد", SettingsPages.of(key))
        assertNotNull("$key ⇐ ${SettingsPaths.section(key)}", table.resolve(SettingsPaths.section(key)))
        val inCategory = SettingsLayout.visible(key, desktop, SettingsLayout.SERVER_GATED, everyFeature)
        val found = SettingsSearchIndex.search(docs, SettingsLayout.labelOf(key)).any { it.key == key }
        assertTrue("$key ($desktop) نه در دسته است نه در جست‌وجو", found)
        assertTrue("$key ($desktop) بی‌راه", inCategory || key in SettingsTiers.ALWAYS_ON || key in SettingsLayout.EXPERIMENTAL)
      }
    }
    //  دسته‌ها و «قابلیت‌ها» نشانیِ خودشان را دارند
    SettingsLayout.GROUPS.forEach { assertNotNull(it.title, table.resolve(SettingsPaths.home(it.title))) }
    assertNotNull(table.resolve(SettingsPaths.FEATURES))
  }

  @Test
  fun `«قابلیت‌ها» — هر کارت همان کلیدِ صفحهٔ خودش و گزینه‌هایش فقط با «روشن» در دسته`() {
    assertEquals(11, SettingsLayout.FEATURES.size)
    assertEquals(SettingsLayout.FEATURES.size, SettingsLayout.FEATURES.toSet().size)
    SettingsLayout.FEATURES.forEach { key ->
      assertNotNull("$key صفحه ندارد", SettingsPages.of(key))
      assertNotNull("$key دسته ندارد", SettingsLayout.groupOf(key))
      assertTrue("$key خطِ ساده ندارد", !SettingsHelp.line(key).isNullOrBlank())
      assertEquals(key, SettingsTiers.Tier.ADVANCED, SettingsTiers.tier(key))
      assertFalse(key, key in SettingsLayout.EXPERIMENTAL)
    }
    (SettingsLayout.FEATURE_GATED).forEach { key ->
      assertFalse("$key خاموش است ولی ردیف دارد", SettingsLayout.visible(key, false, SettingsLayout.SERVER_GATED, emptySet()))
      assertTrue("$key روشن است ولی ردیف ندارد", SettingsLayout.visible(key, false, SettingsLayout.SERVER_GATED, setOf(key)))
    }
    //  «حساب‌پیِ دکان» کلیدِ روشن/خاموش ندارد و ردیفش همیشه در «پرداخت» است
    assertTrue(SettingsLayout.visible(SettingsLayout.HESAB_SHOP, false, emptySet(), emptySet()))
    assertEquals("pay", SettingsLayout.partOf(SettingsLayout.HESAB_SHOP))
    //  کارت همان نوشتنِ صفحهٔ خودش را صدا می‌زند — هیچ ذخیرهٔ تازه‌ای نیست
    val bind = src("ui/v3/settings/FeatureSwitchesAndroid.kt")
    listOf(
      "GiftCardPref.set(", "LayawayPref.set(", "LoyaltyPref.set(", "SafeBookPref.set(", "SpecialOrderPref.set(", "QuotePref.set(",
      "PriceListPrefs.set(", "DeliverLaterPrefs.set(", "TaxQuarterPref.setOn(",
    ).forEach { assertTrue("$it در کارت‌ها نیست", bind.contains(it)) }
    assertFalse(bind.contains("getSharedPreferences"))
    assertFalse(bind.contains("putBoolean"))
    //  صفحهٔ اول فهرستِ قابلیت‌ها را ندارد؛ فقط پیوند
    val home = src("ui/v3/settings/SettingsIndexScreen.kt").substringAfter("internal fun RouteScope.SettingsIndexScreen(").substringBefore("\n}\n")
    assertFalse(home.contains("FeaturesScreen("))
  }

  @Test
  fun `جست‌وجو — نرمال‌سازیِ فارسی، دری، انگلیسی و پشتو و غلطِ تایپی`() {
    val docs = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet())
    fun keys(q: String) = SettingsSearchIndex.search(docs, q).map { it.key }
    assertTrue(keys("").isEmpty())
    assertTrue(keys("   ").isEmpty())
    assertTrue(SettingsLayout.PRINTER in keys("رسید"))
    //  ي/ك عربی و نیم‌فاصله
    assertTrue(SettingsLayout.PRINTER in keys("رسيد"))
    assertTrue("همگام‌سازی" in keys("همگام سازی"))
    //  هم‌معنی: «رمز» قفلِ برنامه را هم پیدا می‌کند؛ عنوانی که با پرس‌وجو آغاز می‌شود اول است
    assertTrue("قفل برنامه" in keys("رمز"))
    assertEquals("قفل برنامه", keys("قفل").first())
    //  واژهٔ دری روی صفحهٔ فارسی، و انگلیسی و پشتو
    assertTrue(SettingsLayout.GOODS in keys("گدام"))
    assertTrue(SettingsLayout.STOCK_PLACES in keys("گدام"))
    assertTrue(SettingsLayout.PRINTER in keys("receipt"))
    assertTrue(SettingsLayout.GIFT_CARD in keys("ډالۍ"))
    //  غلطِ تایپی
    assertTrue(SettingsLayout.TAX_QUARTER in keys("مالیت"))
    //  «قابلیت‌ها» و دسته‌ها هم پیدا می‌شوند
    assertTrue(SettingsSearchIndex.FEATURES_KEY in keys("قابلیت"))
    assertEquals(SettingsLayout.SHOP, keys("دکان و امنیت").first())
    //  نتیجهٔ بخش جایش را می‌گوید: «دسته · بخش»
    val printer = SettingsSearchIndex.search(docs, "چاپگر").first { it.key == SettingsLayout.PRINTER }
    assertEquals("فروش و پرداخت · رسید، برگشت و مشتری", printer.place)
    //  صفحهٔ انگلیسی: عنوانِ انگلیسی، و واژهٔ فارسی هنوز پیدا می‌کند
    I18n.source = { Lang.EN }
    val en = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet())
    assertTrue(SettingsSearchIndex.search(en, "lock").any { it.key == "قفل برنامه" })
    assertTrue(SettingsSearchIndex.search(en, "رسید").any { it.key == SettingsLayout.PRINTER })
    //  صفحهٔ دری
    I18n.source = { Lang.PRS }
    val prs = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet())
    assertTrue(SettingsSearchIndex.search(prs, "گدام").any { it.key == SettingsLayout.GOODS })
    assertTrue(SettingsSearchIndex.search(prs, "انبار").any { it.key == SettingsLayout.GOODS })
  }

  /** PAY-13 — «شمارشِ کور» گزینهٔ صفحهٔ «پرداخت» است؛ جست‌وجو به همان صفحه می‌برد، به هر سه زبان */
  @Test
  fun `جست‌وجوی شمارشِ کور به صفحهٔ پرداخت می‌رسد`() {
    val docs = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet())
    assertTrue(SettingsSearchIndex.search(docs, "شمارش کور").any { it.key == SettingsLayout.PAY })
    assertTrue(SettingsSearchIndex.search(docs, "blind count").any { it.key == SettingsLayout.PAY })
    assertTrue(SettingsSearchIndex.search(docs, "پټ شمېرل").any { it.key == SettingsLayout.PAY })
    val desk = SettingsSearchIndex.docs(desktop = true, serverOn = emptySet())
    assertTrue(SettingsSearchIndex.search(desk, "شمارش کور").any { it.key == SettingsLayout.PAY })
  }

  @Test
  fun `جست‌وجو فقط بخشِ همین سکو و پرچمِ روشنِ سرور را دارد و هر نتیجه به صفحه‌اش می‌رسد`() {
    val phone = SettingsSearchIndex.docs(desktop = false, serverOn = emptySet()).map { it.key }
    val desk = SettingsSearchIndex.docs(desktop = true, serverOn = emptySet()).map { it.key }
    SettingsLayout.PHONE_HIDDEN.forEach { assertFalse(it, it in phone); assertTrue(it, it in desk) }
    SettingsLayout.DESK_HIDDEN.forEach { assertFalse(it, it in desk); assertTrue(it, it in phone) }
    SettingsLayout.SERVER_GATED.forEach { assertFalse(it, it in phone) }
    assertEquals("کلیدِ تکراری در نمایه", phone.size, phone.toSet().size)
    val table = V3Modules.table
    SettingsSearchIndex.docs(desktop = false, serverOn = SettingsLayout.SERVER_GATED).forEach { d ->
      assertNotNull("${d.key} ⇐ ${SettingsPaths.of(d)}", table.resolve(SettingsPaths.of(d)))
    }
    //  ردیفِ ماژول (پوزش و کوپنِ تأخیر) با مسیرِ خودش
    val extra = SettingsSearchIndex.docs(false, emptySet(), listOf(SettingsSearchIndex.Extra(SettingsPaths.LATE_APOLOGY, "پوزش", null, SettingsLayout.SALES)))
    assertEquals(SettingsPaths.LATE_APOLOGY, SettingsPaths.of(extra.first { it.kind == Kind.EXTRA }))
    //  واژه‌های جست‌وجو فقط برای کلیدهای واقعی، و هر بخش واژه دارد
    val known = allKeys.toSet() + SettingsLayout.GROUPS.map { it.title } + SettingsSearchIndex.FEATURES_KEY
    SettingsSearchIndex.KEYWORDS.keys.forEach { assertTrue("واژهٔ کلیدِ ناشناس: $it", it in known) }
    known.forEach { assertTrue("$it واژهٔ جست‌وجو ندارد", SettingsSearchIndex.keywords(it).size >= 3) }
  }
}
