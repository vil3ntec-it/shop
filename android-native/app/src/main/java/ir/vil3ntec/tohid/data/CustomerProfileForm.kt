package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.LocaleText

/**
 *  پروندهٔ مشتری در صفحه‌های قدیمِ قرض‌داران (گوشی) و مشتریانِ کامپیوتر — CRM-02 (شمارهٔ دوم)،
 *  CRM-11 (برچسب و صافیِ برچسب) و فهرستِ بی ادغام‌شده‌ها (CRM-10).
 *
 *  منطقِ خالص، بی android.*، زیرِ آزمون (`CustomerProfileFormTest`)؛ همان قاعدهٔ `DebtorFormsModel` ِ
 *  نسخهٔ ۳ (#393) روی همان موتورها: `DebtorEngine.add/editWithLimit` و `CustomerCrm.setProfile`.
 *  ذخیره همه یا هیچ است؛ گروه و لیستِ قیمت (پول) از این فرم دست نمی‌خورند.
 *
 *  صفحه این‌ها را فقط با «گزارش‌های مشتری» روشن نشان می‌دهد (`ShopPolicy.crmReports`، پیش‌فرض
 *  خاموش)؛ خاموش ⇒ فرم و جست‌وجوی همان امروز.
 */
object CustomerProfileForm {

  /** برچسب‌ها ⇐ متنِ کادر («عمده، همسایه») */
  fun tagsText(debtor: Debtor?): String = debtor?.tags.orEmpty().joinToString("، ")

  /**
   *  قرض‌دارِ تازه یا ویرایش، و سپس شمارهٔ دوم و برچسب‌ها. هر گام رد کند ⇒ همان پیامِ موتور و
   *  دفترِ دست‌نخورده (هیچ نیمه‌ذخیره‌ای نیست).
   */
  fun save(
    d: ShopData,
    editingId: String?,
    draft: DebtorEngine.DebtorDraft,
    phone2: String,
    tagsText: String,
    now: Long,
    newId: () -> String,
  ): DebtorEngine.Result {
    val base = if (editingId == null) DebtorEngine.add(d, draft, now, newId) else DebtorEngine.editWithLimit(d, editingId, draft)
    val saved = base as? DebtorEngine.Result.Ok ?: return base
    return setProfile(saved.data, saved.id, phone2, tagsText)
  }

  /** فقط شمارهٔ دوم و برچسب‌ها (پروندهٔ کامپیوتر) — بقیهٔ پرونده همان */
  fun setProfile(d: ShopData, id: String, phone2: String, tagsText: String): DebtorEngine.Result {
    val c = d.debtors.find { it.id == id } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    val p = CustomerCrm.profileOf(c).copy(phone2 = phone2, tags = CustomerCrm.parseTags(tagsText))
    return when (val r = CustomerCrm.setProfile(d, id, p)) {
      is DebtorEngine.Result.Ok -> DebtorEngine.Result.Ok(r.data, id)
      is DebtorEngine.Result.Failed -> r
    }
  }

  /**
   *  مشتریانِ فهرست. ادغام‌شده‌ها همیشه بیرون‌اند (`CustomerCrm.active`) — بی ادغام همان فهرستِ امروز.
   *  @param crm روشن ⇒ جست‌وجو روی نام، هر دو شماره و برچسب (`CustomerCrm.matches`، «#برچسب» هم) و
   *    صافیِ [tag]؛ خاموش ⇒ همان جست‌وجوی امروز (نام و شمارهٔ اول) و [tag] بی‌اثر.
   */
  fun visible(d: ShopData, query: String, tag: String?, crm: Boolean): List<Debtor> {
    val q = query.trim()
    return CustomerCrm.active(d).filter { c ->
      if (crm) {
        CustomerCrm.matches(c, q) && (tag.isNullOrBlank() || CustomerCrm.hasTag(c, tag))
      } else {
        q.isEmpty() || c.name.contains(q, ignoreCase = true) || LocaleText.phoneMatches(c.phone, q)
      }
    }
  }

  /** نوشتهٔ کوتاهِ زیرِ نام: شمارهٔ دوم و برچسب‌ها («#عمده #همسایه») — خالی ⇒ "" */
  fun extraLine(debtor: Debtor): String = listOfNotNull(
    debtor.phone2?.takeIf { it.isNotBlank() }?.let { LocaleText.phoneDisplay(it) },
    debtor.tags.orEmpty().takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
  ).joinToString(" · ")
}
