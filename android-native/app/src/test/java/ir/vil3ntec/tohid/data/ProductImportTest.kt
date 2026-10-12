package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ورودِ کالا از CSV — هیچ ردیفی بی‌صدا گم نمی‌شود */
class ProductImportTest {

  private val d = ShopData(products = listOf(Product(id = "p1", name = "برنج", barcodes = listOf("111"))))

  @Test
  fun `سرستون فارسی، گیومه، رقم فارسی و ردیف های رد شده`() {
    val csv = "﻿نام محصول,دسته,واحد,قیمت خرید,قیمت فروش,حداقل موجودی,بارکد\r\n" +
      "\"روغن، ۵ لیتری\",خوراکی,عدد,\"۱٬۲۰۰\",1500,2,222\n" +
      "برنج,خوراکی,کیلوگرم,100,120,0,\n" +
      ",,,,,,\n" +
      "شکر,,کیلوگرم,abc,10,0,\n" +
      "چای,,بسته,50,70,0,111\n" +
      "روغن، ۵ لیتری,,عدد,1,2,0,\n"
    val plan = ProductImport.plan(d, csv)
    assertEquals(listOf("روغن، ۵ لیتری"), plan.drafts.map { it.name })
    assertEquals(1200.0, plan.drafts[0].purchasePrice, 0.0)
    assertEquals("222", plan.drafts[0].barcode)
    assertEquals(listOf(3, 5, 6, 7), plan.skipped.map { it.row })
    assertTrue(plan.skipped.first().reason.contains("از قبل"))
  }

  @Test
  fun `جداکننده نقطه ویرگول و سرستون انگلیسی`() {
    val plan = ProductImport.plan(d, "name;unit;price\nصابون;عدد;25\n")
    assertEquals(1, plan.drafts.size)
    assertEquals(25.0, plan.drafts[0].salePrice, 0.0)
  }

  @Test
  fun `بی ستون نام`() {
    assertTrue(ProductImport.plan(d, "a,b\n1,2\n").missingName)
  }
}
