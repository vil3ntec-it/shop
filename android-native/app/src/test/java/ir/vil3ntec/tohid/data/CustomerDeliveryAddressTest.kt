package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.ui.v3.money.DebtorFormsModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-09 — نشانیِ تحویلِ پروندهٔ مشتریِ دکان (قرض‌دار)، §۴.۴: فیلدِ تازهٔ همگام‌شوندهٔ v1 با همان الگوی `phone2`/`cardCode`
 *  (`EncodeDefault.NEVER`، بی مهاجرت): قرض‌دارِ قدیمی بایت‌به‌بایت همان؛ op ِ Sync v1 فقط همین فیلد؛ ساختِ پیشین که کلید را
 *  نمی‌شناسد آن را پاک نمی‌کند؛ سنجش و پاک‌سازی، ادغام، و برگهٔ نسخهٔ ۳.
 */
class CustomerDeliveryAddressTest {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val d0 = ShopData(debtors = listOf(Debtor(id = "c1", name = "احمد", phone = "+93700123456"), Debtor(id = "c2", name = "محمود")))
  private fun ok(r: DebtorEngine.Result): ShopData = (r as DebtorEngine.Result.Ok).data
  private fun failed(r: DebtorEngine.Result): String = (r as DebtorEngine.Result.Failed).message
  private val home = DeliveryAddress(area = "کابل، ناحیهٔ ۵، کوچهٔ گل", landmark = "پشتِ مسجدِ جامع", gate = "دروازهٔ سبز", floor = "۲", plusCode = "v75v+8q", postalCode = "۱۰۰۶")

  @Test fun `قرض‌دارِ بی نشانی بایت‌به‌بایت همان؛ نشانی فقط بخش‌های پر را می‌نویسد و برمی‌گردد`() {
    val old = Debtor(id = "c1", name = "احمد", phone = "0700", notes = "n", createdAt = 5)
    assertEquals("""{"id":"c1","name":"احمد","phone":"0700","notes":"n","createdAt":5}""", json.encodeToString(Debtor.serializer(), old))
    val with = old.copy(deliveryAddress = DeliveryAddress(landmark = "چوکِ کوته‌سنگی", gate = "سبز"))
    val text = json.encodeToString(Debtor.serializer(), with)
    assertTrue(text, text.endsWith(""","deliveryAddress":{"landmark":"چوکِ کوته‌سنگی","gate":"سبز"}}"""))
    assertEquals(with, json.decodeFromString(Debtor.serializer(), text))
  }

  @Test fun `نوشتن — پاک‌سازی، Plus Code بزرگ، رقمِ لاتین؛ همان نشانی بی تغییر؛ همه خالی ⇒ برداشتن`() {
    val d = ok(CustomerCrm.setDeliveryAddress(d0, "c1", home))
    val a = d.debtors.first { it.id == "c1" }.deliveryAddress!!
    assertEquals("V75V+8Q", a.plusCode)
    assertEquals("1006", a.postalCode)
    assertEquals("پشتِ مسجدِ جامع", a.landmark)
    assertEquals("پشتِ مسجدِ جامع · کابل، ناحیهٔ ۵، کوچهٔ گل · دروازهٔ سبز · ۲ · V75V+8Q · 1006", CustomerCrm.addressLine(a))
    assertNull(d.debtors.first { it.id == "c2" }.deliveryAddress)
    assertSame(d, ok(CustomerCrm.setDeliveryAddress(d, "c1", a)))
    val cleared = ok(CustomerCrm.setDeliveryAddress(d, "c1", DeliveryAddress(area = "  ", landmark = "")))
    assertNull(cleared.debtors.first { it.id == "c1" }.deliveryAddress)
    assertEquals("", CustomerCrm.addressLine(null))
  }

  @Test fun `سنجش — Plus Code و کدِ پستیِ نادرست، بخشِ بلند، مشتریِ ناشناس یا ادغام‌شده`() {
    assertTrue(failed(CustomerCrm.setDeliveryAddress(d0, "c1", DeliveryAddress(plusCode = "ABCD+12"))).startsWith("Plus Code"))
    assertEquals("کدِ پستی فقط رقم است", failed(CustomerCrm.setDeliveryAddress(d0, "c1", DeliveryAddress(postalCode = "10-06"))))
    assertTrue(failed(CustomerCrm.setDeliveryAddress(d0, "c1", DeliveryAddress(landmark = "x".repeat(121)))).contains("120"))
    assertEquals("مشتری پیدا نشد", failed(CustomerCrm.setDeliveryAddress(d0, "x", home)))
    val merged = d0.copy(debtors = d0.debtors.map { if (it.id == "c2") it.copy(mergedInto = "c1") else it })
    assertEquals("این مشتری با مشتریِ دیگری ادغام شده است", failed(CustomerCrm.setDeliveryAddress(merged, "c2", home)))
  }

  @Test fun `Sync v1 — op فقط همان فیلد (یک شیء)؛ برداشتن null ِ صریح؛ ساختِ پیشین که کلید را ندارد پاکش نمی‌کند`() {
    val d = ok(CustomerCrm.setDeliveryAddress(d0, "c1", home))
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(d0), LedgerDiff.toJson(d))
    val op = ops.single()
    assertEquals(SyncOp.UPDATE, op.type)
    assertEquals("debtors", op.table)
    assertEquals(setOf("deliveryAddress"), op.fields!!.keys)
    assertEquals("V75V+8Q", op.fields!!["deliveryAddress"]!!.jsonObject["plusCode"]!!.jsonPrimitive.content)
    //  برداشتن ⇒ null ِ صریح (همین ساخت فیلد را می‌شناسد)
    val gone = ok(CustomerCrm.setDeliveryAddress(d, "c1", DeliveryAddress()))
    assertEquals(JsonNull, LedgerDiff.diff(LedgerDiff.shadowOf(d), LedgerDiff.toJson(gone)).single().fields!!["deliveryAddress"])
    //  ساختِ پیشین: `isModelField` ِ آن ساخت این کلید را ندارد ⇒ نبودنش در دفترش «نمی‌شناسم» است، نه «پاک شد»
    //  (همان سازوکارِ A00؛ سرور هم `newer-fields.js`). در همین ساخت کلید شناخته است و برداشتنِ آگاهانه null می‌فرستد.
    assertTrue(LedgerDiff.isModelField("debtors", "deliveryAddress"))
    assertTrue(!LedgerDiff.isModelField("debtors", "deliveryAddressV2"))
  }

  @Test fun `ادغام — نشانیِ ماندگار مقدم، بی آن نشانیِ تکراری`() {
    val d = ok(CustomerCrm.setDeliveryAddress(d0, "c2", home))
    val keep = d.debtors.first { it.id == "c1" }
    val drop = d.debtors.first { it.id == "c2" }
    val r = CustomerMerge.preview(d, keep.id, drop.id)
    val survivor = (r as CustomerMerge.Result.Ok).preview.result
    assertEquals("V75V+8Q", survivor.deliveryAddress?.plusCode)
  }

  @Test fun `برگهٔ نسخهٔ ۳ — نشانی خوانده و همراهِ بقیه ذخیره می‌شود؛ نشانیِ نادرست هیچ چیز را نمی‌نویسد`() {
    val withAddr = ok(CustomerCrm.setDeliveryAddress(d0, "c1", home))
    val form = DebtorFormsModel.of(withAddr.debtors.first { it.id == "c1" })
    assertEquals("V75V+8Q", form.address.plusCode)
    val edited = form.copy(name = "احمد خان", address = form.address.copy(floor = "۳"))
    val saved = ok(DebtorFormsModel.save(withAddr, "c1", edited, 1, { "n" }))
    val c1 = saved.debtors.first { it.id == "c1" }
    assertEquals("احمد خان", c1.name)
    assertEquals("۳", c1.deliveryAddress?.floor)
    val bad = DebtorFormsModel.save(withAddr, "c1", edited.copy(address = edited.address.copy(postalCode = "abc")), 1, { "n" })
    assertEquals("کدِ پستی فقط رقم است", failed(bad))
  }
}
