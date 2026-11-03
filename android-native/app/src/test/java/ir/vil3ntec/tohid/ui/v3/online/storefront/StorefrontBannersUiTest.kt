package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.AreaCellDto
import ir.vil3ntec.tohid.data.repo.AreaContactDto
import ir.vil3ntec.tohid.data.repo.AreaRequestsDto
import ir.vil3ntec.tohid.data.repo.StorefrontBannersRepository
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontPermsDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  WEB-24 (فرمِ بنر در برنامهٔ مدیریت) و CHK-10 (فهرستِ «خبرم کن» در برنامه): برگه فقط با کلیدِ سرور، بدنهٔ
 *  همان `PUT /storefront/banners`، روزِ دکان، متنِ هر خطا، و سیم‌کشیِ صفحهٔ ویترین.
 */
class StorefrontBannersUiTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `برگهٔ بنرها فقط با کلیدِ banners ِ سرور و اجازهٔ مدیریت`() {
    val manage = StorefrontPermsDto(manage = true)
    assertTrue(StorefrontModel.BANNERS !in StorefrontModel.tabs(StorefrontConfigDto(permissions = manage)))
    assertTrue(StorefrontModel.BANNERS !in StorefrontModel.tabs(StorefrontConfigDto(banners = true)))
    assertEquals(StorefrontModel.BANNERS, StorefrontModel.tabs(StorefrontConfigDto(banners = true, zones = true, permissions = manage)).last())
    assertEquals(StorefrontModel.BANNERS, StorefrontModel.initialTab(StorefrontModel.BANNERS, StorefrontConfigDto(banners = true, permissions = manage)))
    assertEquals(StorefrontModel.STATUS, StorefrontModel.initialTab(StorefrontModel.BANNERS, StorefrontConfigDto()))
    //  پیوندِ «بنرها» پیش از بارِ تنظیمات نگه داشته می‌شود (پس از بار، نبودنش ⇒ وضعیت)
    assertEquals(StorefrontModel.BANNERS, StorefrontModel.initialTab(StorefrontModel.BANNERS, null))
    assertTrue(StorefrontModel.label(StorefrontModel.BANNERS).isNotBlank() && StorefrontModel.label(StorefrontModel.BANNERS) != StorefrontModel.BANNERS)
  }

  @Test
  fun `روزِ دکان — آغازِ روز به وقتِ کابل و برگشتش`() {
    val start = StorefrontOwner.dayStartMs("2026-11-02", "Asia/Kabul")!!
    //  ۲۰۲۶-۱۱-۰۱ ساعتِ ۱۹:۳۰ ِ UTC = نیمه‌شبِ کابل
    assertEquals(1_793_561_400_000L, start)
    assertEquals("2026-11-02", StorefrontOwner.dayOfMs(start, "Asia/Kabul"))
    assertEquals("2026-11-01", StorefrontOwner.dayOfMs(start - 1, "Asia/Kabul"))
    assertNull(StorefrontOwner.dayStartMs("2026-02-30", "Asia/Kabul"))
    assertNull(StorefrontOwner.dayStartMs("۱۴۰۵-۰۸-۱۱", "Asia/Kabul"))
  }

  @Test
  fun `بدنهٔ ذخیره همان کلیدهای سرور است`() {
    val b = StorefrontBanners.Banner("روغن", "تخفیف", "product", "oil", listOf("Z1"), 10L, 20L, 7)
    val body = StorefrontBannersRepository.body(listOf(b))
    val one = body["banners"]!!.jsonArray.single().jsonObject
    assertEquals(setOf("title", "body", "startAt", "endAt", "sort", "zoneIds", "link"), one.keys)
    assertEquals(10L, one["startAt"]!!.jsonPrimitive.long)
    assertEquals(20L, one["endAt"]!!.jsonPrimitive.long)
    assertEquals(7, one["sort"]!!.jsonPrimitive.int)
    assertEquals(listOf("Z1"), one["zoneIds"]!!.jsonArray.map { it.jsonPrimitive.content })
    assertEquals("product" to "oil", one["link"]!!.jsonObject.let { it["type"]!!.jsonPrimitive.content to it["value"]!!.jsonPrimitive.content })
    //  فهرستِ خالی = پاک کردنِ همه
    assertEquals(0, StorefrontBannersRepository.body(emptyList())["banners"]!!.jsonArray.size)
  }

  @Test
  fun `هر خطا متنِ خودش را دارد و پیوند فقط کالای فروختنی`() {
    for (lang in listOf(Lang.FA, Lang.EN, Lang.PS, Lang.PRS)) {
      I18n.source = { lang }
      val texts = StorefrontBanners.Err.entries.associateWith { BannersText.error(it) }
      assertTrue(lang.name, texts.values.all { it.isNotBlank() })
      assertTrue(lang.name, StorefrontBanners.When.entries.map { BannersText.whenLabel(it) }.toSet().size == 3)
    }
    val p = listOf(
      StorefrontProductDto("oil", "روغن", category = "خوراکی", published = true),
      StorefrontProductDto("soap", "صابون", category = "بهداشتی", published = false),
      StorefrontProductDto("old", "روغنِ کهنه", category = "خوراکی", published = true, active = false),
      StorefrontProductDto("rice", "برنج", category = " خوراکی ", published = true),
    )
    assertEquals(listOf("oil"), BannersText.productMatches(p, "روغن").map { it.id })
    assertEquals(listOf("oil", "rice"), BannersText.productMatches(p, "").map { it.id })
    assertEquals(listOf("خوراکی"), BannersText.categories(p))
  }

  @Test
  fun `فهرستِ خبرم کن از پاسخِ سرور، بی‌پوشش اول`() {
    val v = AreaRequestsDto(
      total = 3, covered = 1,
      cells = listOf(AreaCellDto(34.5, 69.1, 1, "مرکز"), AreaCellDto(34.7, 69.3, 2, null)),
      contacts = listOf(AreaContactDto("r1", "+93791200003", "مرکز", 5)),
    ).view()
    assertEquals(listOf(null, "مرکز"), v.cells.map { it.coveredBy })
    assertEquals(listOf("+93791200003"), v.contacts.map { it.phone })
  }

  @Test
  fun `سیم‌کشی — برگهٔ بنرها، ذخیره با پاسخِ سرور و فهرستِ خبرم کن زیرِ ارسال و زمان`() {
    val route = src("ui/v3/online/storefront/StorefrontRouteContent.kt")
    assertTrue(route.contains("StorefrontModel.BANNERS -> BannersPane("))
    assertTrue(route.contains("V3FormBar(StrOnline.bnSave"))
    assertTrue("نوشتهٔ ذخیره‌نشدهٔ بنرها با برگشت پرسیده می‌شود", route.contains("|| banners.dirty)"))
    assertTrue(route.contains("ZonesPane(zonesForm, zonesErrors, { zonesForm = it }, pad, notifyArea = c.notifyArea)"))
    val zones = src("ui/v3/online/storefront/ZonesPane.kt")
    assertTrue(zones.contains("if (notifyArea) AreaRequestsSection()"))
    val area = src("ui/v3/online/storefront/AreaRequestsSection.kt")
    assertTrue(area.contains("AreaRequestsRepository(Backend.api(context))"))
    assertTrue(area.contains("uri.openUri(\"tel:\" + c.phone)"))
    //  پس از «خبر دادم» (یا ۴۰۴/۴۰۹) فهرستِ تازه از سرور — ردیفِ کهنه نمی‌ماند
    assertTrue(area.contains("repo.notified(c.id)") && area.contains("load()\n"))
    val pane = src("ui/v3/online/storefront/BannersPane.kt")
    //  «ثبت شد» فقط با پاسخِ ۲xx و فرم از همان پاسخ
    assertTrue(pane.contains("r.save(list).onSuccess { d -> max = d.max; show(d.banners.map { it.banner() }, tz) }"))
    assertTrue(pane.contains("StorefrontOwner.dayStartMs(it, tz)"))
  }
}
