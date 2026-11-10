package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
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

  /* ------------------------------ پینِ نشانی (CRM-09، §۴.۴: «پین منبعِ اصلی») ------------------------------ */

  @Test fun `پین — شش رقمِ اعشار با گردِ سرور؛ هر دو یا هیچ؛ نه بیرونِ بازه و نه صفر و صفر؛ همان پین بی op`() {
    val d = ok(CustomerCrm.setDeliveryPin(d0, "c1", 34.5000005, 69.1723456))
    val c1 = d.debtors.first { it.id == "c1" }
    assertEquals(DeliveryPin(34.500001, 69.172346), c1.deliveryPin)
    assertNull("پین نشانی را نمی‌سازد", c1.deliveryAddress)
    assertEquals("34.50000, 69.17235", CustomerCrm.addressLineOf(c1))
    assertSame(d, ok(CustomerCrm.setDeliveryPin(d, "c1", 34.500001, 69.172346)))
    val withAddr = ok(CustomerCrm.setDeliveryAddress(d, "c1", home)).debtors.first { it.id == "c1" }
    assertEquals("پشتِ مسجدِ جامع · کابل، ناحیهٔ ۵، کوچهٔ گل · دروازهٔ سبز · ۲ · V75V+8Q · 1006 · 34.50000, 69.17235", CustomerCrm.addressLineOf(withAddr))
    assertNull(ok(CustomerCrm.setDeliveryPin(d, "c1", null, null)).debtors.first { it.id == "c1" }.deliveryPin)
    for ((lat, lng) in listOf(34.53 to null, null to 69.17, 95.0 to 69.17, 0.0 to 0.0, Double.NaN to 69.17)) {
      assertEquals(StrLedger.addressPinBad, failed(CustomerCrm.setDeliveryPin(d0, "c1", lat, lng)))
    }
    assertEquals("مشتری پیدا نشد", failed(CustomerCrm.setDeliveryPin(d0, "x", 34.53, 69.17)))
    val merged = d0.copy(debtors = d0.debtors.map { if (it.id == "c2") it.copy(mergedInto = "c1") else it })
    assertEquals("این مشتری با مشتریِ دیگری ادغام شده است", failed(CustomerCrm.setDeliveryPin(merged, "c2", 34.53, 69.17)))
  }

  @Test fun `پین فیلدی جدا از نشانی — قرض‌دارِ بی پین بایت‌به‌بایت همان؛ op فقط همان فیلد؛ ساختِ پیشین که کلید را ندارد پاکش نمی‌کند`() {
    val old = Debtor(id = "c1", name = "احمد", phone = "0700", notes = "n", createdAt = 5, deliveryAddress = DeliveryAddress(landmark = "مسجد"))
    assertEquals("""{"id":"c1","name":"احمد","phone":"0700","notes":"n","createdAt":5,"deliveryAddress":{"landmark":"مسجد"}}""",
      json.encodeToString(Debtor.serializer(), old))
    val withPin = old.copy(deliveryPin = DeliveryPin(34.53, 69.17))
    val text = json.encodeToString(Debtor.serializer(), withPin)
    assertTrue(text, text.endsWith(""","deliveryAddress":{"landmark":"مسجد"},"deliveryPin":{"lat":34.53,"lng":69.17}}"""))
    assertEquals(withPin, json.decodeFromString(Debtor.serializer(), text))
    val d = ok(CustomerCrm.setDeliveryPin(d0, "c1", 34.53, 69.17))
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(d0), LedgerDiff.toJson(d)).single()
    assertEquals(setOf("deliveryPin"), op.fields!!.keys)
    assertEquals("34.53", op.fields!!["deliveryPin"]!!.jsonObject["lat"]!!.jsonPrimitive.content)
    //  ⛔ ساختِ پیشین `deliveryPin` را نمی‌شناسد: ویرایشِ نشانی با آن ساخت فقط `deliveryAddress` را می‌فرستد و پین می‌ماند (A00)
    assertTrue(LedgerDiff.isModelField("debtors", "deliveryPin"))
    val edited = ok(CustomerCrm.setDeliveryAddress(d, "c1", home))
    assertEquals(setOf("deliveryAddress"), LedgerDiff.diff(LedgerDiff.shadowOf(d), LedgerDiff.toJson(edited)).single().fields!!.keys)
  }

  @Test fun `ادغام — پین همراهِ نشانیِ ماندگار؛ بی نشانی و پین، هر دو از ادغام‌شونده`() {
    val d = ok(CustomerCrm.setDeliveryPin(ok(CustomerCrm.setDeliveryAddress(d0, "c2", home)), "c2", 34.53, 69.17))
    val r = CustomerMerge.preview(d, "c1", "c2")
    val survivor = (r as CustomerMerge.Result.Ok).preview.result
    assertEquals("V75V+8Q", survivor.deliveryAddress?.plusCode)
    assertEquals(DeliveryPin(34.53, 69.17), survivor.deliveryPin)
    //  ماندگار نشانی دارد و پین ندارد ⇒ پینِ ادغام‌شونده کنارِ نشانیِ دیگری نمی‌نشیند
    val both = ok(CustomerCrm.setDeliveryAddress(d, "c1", DeliveryAddress(landmark = "چوک")))
    val s2 = (CustomerMerge.preview(both, "c1", "c2") as CustomerMerge.Result.Ok).preview.result
    assertEquals("چوک", s2.deliveryAddress?.landmark)
    assertNull(s2.deliveryPin)
  }

  @Test fun `برگهٔ نسخهٔ ۳ — پین از مختصات یا پیوندِ نقشه؛ همان پین بی تغییر؛ پیوندِ کوتاه هیچ چیز را نمی‌نویسد؛ خالی پین را برمی‌دارد`() {
    val withPin = ok(CustomerCrm.setDeliveryPin(ok(CustomerCrm.setDeliveryAddress(d0, "c1", home)), "c1", 34.528123, 69.172346))
    val form = DebtorFormsModel.of(withPin.debtors.first { it.id == "c1" })
    assertEquals("34.528123, 69.172346", form.pinText)
    assertNull(DebtorFormsModel.pinError(form))
    val same = ok(DebtorFormsModel.save(withPin, "c1", form, 1, { "n" })).debtors.first { it.id == "c1" }
    assertEquals(DeliveryPin(34.528123, 69.172346), same.deliveryPin)

    val link = form.copy(pinText = "https://www.google.com/maps/place/x/@34.5312,69.1711,17z")
    val linked = ok(DebtorFormsModel.save(withPin, "c1", link, 1, { "n" })).debtors.first { it.id == "c1" }
    assertEquals(DeliveryPin(34.5312, 69.1711), linked.deliveryPin)
    assertEquals("V75V+8Q", linked.deliveryAddress?.plusCode)

    val short = form.copy(name = "احمد خان", pinText = "https://maps.app.goo.gl/AbCd")
    assertEquals(StrLedger.addressPinBad, DebtorFormsModel.pinError(short))
    assertEquals(StrLedger.addressPinBad, failed(DebtorFormsModel.save(withPin, "c1", short, 1, { "n" })))

    val cleared = ok(DebtorFormsModel.save(withPin, "c1", form.copy(pinText = "  "), 1, { "n" })).debtors.first { it.id == "c1" }
    assertNull(cleared.deliveryPin)
    assertEquals("V75V+8Q", cleared.deliveryAddress?.plusCode)
  }
}
