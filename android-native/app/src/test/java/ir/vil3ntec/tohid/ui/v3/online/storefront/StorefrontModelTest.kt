package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontFulfillmentDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** برگه‌های ویترین، فیلترِ کالاها، فرمِ تنظیمات (همان بدنهٔ پیشین) و جمله‌های وضعیت */
class StorefrontModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  @Test
  fun `برگهٔ ارسال و زمان فقط با پرچمِ ناحیه‌ها`() {
    assertEquals(4, StorefrontModel.tabs(StorefrontConfigDto()).size)
    assertEquals(StorefrontModel.DELIVERY, StorefrontModel.tabs(StorefrontConfigDto(zones = true)).last())
    assertEquals(StorefrontModel.STATUS, StorefrontModel.initialTab(StorefrontModel.DELIVERY, StorefrontConfigDto()))
    assertEquals(StorefrontModel.DELIVERY, StorefrontModel.initialTab(StorefrontModel.DELIVERY, StorefrontConfigDto(zones = true)))
    //  پیش از بارِ تنظیمات پیوندِ «ارسال» نگه داشته می‌شود
    assertEquals(StorefrontModel.DELIVERY, StorefrontModel.initialTab(StorefrontModel.DELIVERY, null))
    assertEquals(StorefrontModel.MARKET, StorefrontModel.initialTab(StorefrontModel.MARKET, null))
    assertEquals(StorefrontModel.STATUS, StorefrontModel.initialTab("x", null))
  }

  @Test
  fun `فیلتر و شمارِ کالاها`() {
    val list = listOf(
      StorefrontProductDto("1", "برنج سیلا", published = true, priceMinor = 12_000),
      StorefrontProductDto("2", "روغن", published = false),
      StorefrontProductDto("3", "Rice basmati", published = true, onlinePriceMinor = 9_500),
    )
    assertEquals(2, StorefrontModel.count(list, StorefrontModel.Filter.ON))
    assertEquals(1, StorefrontModel.count(list, StorefrontModel.Filter.OFF))
    assertEquals(listOf("3"), StorefrontModel.filter(list, " rice ", StorefrontModel.Filter.ALL).map { it.id })
    assertEquals(emptyList<String>(), StorefrontModel.filter(list, "روغن", StorefrontModel.Filter.ON).map { it.id })
    assertTrue(StorefrontModel.priceText(list[2]).contains("95"))
    assertEquals(StrOnline.noPrice, StorefrontModel.priceText(list[1]))
    assertNull(StorefrontModel.ledgerPrice(list[1]))
    assertNotNull(StorefrontModel.ledgerPrice(list[0]))
    assertEquals(StrOnline.sfInactive, StorefrontModel.stateLabel(list[0].copy(active = false)))
  }

  @Test
  fun `فرمِ تنظیمات از سرور و برگشت`() {
    val s = StorefrontSettingsDto(
      slug = "my-shop", title = "دکانِ نمونه", contactPhone = "+93700000000", enabled = true,
      fulfillment = StorefrontFulfillmentDto(delivery = true, pickup = false), deliveryFeeMinor = 5_000, minOrderMinor = 25_050,
      weeklyHours = mapOf("0" to listOf(listOf(480, 1200))), closedDates = listOf("2026-10-30"),
    )
    val f = StorefrontSettingsModel.from(s)
    assertEquals("0700000000", f.phone)
    assertEquals("50", f.fee)
    assertEquals("250.5", f.minOrder)
    assertEquals("08:00-20:00", f.days[0])
    val r = StorefrontSettingsModel.build(f)
    assertTrue(r.errors.toString(), r.errors.isEmpty())
    val body = r.body!!
    assertEquals("my-shop", body["slug"]!!.jsonPrimitive.content)
    assertEquals(5_000L, body["deliveryFeeMinor"]!!.jsonPrimitive.long)
    assertEquals(25_050L, body["minOrderMinor"]!!.jsonPrimitive.long)
    assertFalse(body["acceptPickup"]!!.jsonPrimitive.boolean)
    assertEquals("my-shop.vill3n.top", StorefrontSettingsModel.address(" My-Shop "))
  }

  @Test
  fun `خطاهای فرمِ تنظیمات`() {
    val bad = StorefrontSettingsModel.Form(slug = "", enabled = true, accent = "red", delivery = false, pickup = false, fee = "x", days = mapOf(2 to "25:00-26:00"))
    val e = StorefrontSettingsModel.build(bad).errors
    assertNull(StorefrontSettingsModel.build(bad).body)
    assertEquals(setOf("slug", "title", "accent", "fulfillment", "fee", "day2"), e.keys)
  }

  @Test
  fun `جمله‌های وضعیت و ساعتِ اعلام`() {
    assertEquals(20 * 60 + 30, PresenceModel.untilMinutes("۲۰:۳۰"))
    assertNull(PresenceModel.untilMinutes("25:00"))
    assertNull(PresenceModel.untilMinutes("abc"))
    assertEquals("auto", PresenceModel.MODES.last())
    val st = PresenceStatusDto(badge = "open", localTime = "10:15", hoursSet = true, today = listOf(listOf(480, 1200)))
    assertTrue(PresenceModel.caption(st).contains("10:15"))
    assertTrue(PresenceModel.caption(st).contains("08:00-20:00"))
    assertTrue(PresenceModel.headline(st).isNotBlank())
    I18n.source = { Lang.EN }
    assertTrue(PresenceModel.caption(st.copy(holiday = true)).none { it in '\u0600'..'\u06FF' })
  }
}

