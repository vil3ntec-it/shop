package ir.vil3ntec.tohid.ui.screens

/*
 *  RSK-08 — «پروندهٔ مدرک»: فقط یاورِ پولِ مشترکِ صفحهٔ نسخهٔ ۳ (`ui/v3/online/orders/OrderEvidenceV3`)، چاپ
 *  (`OrderEvidencePrintAndroid`) و پوزش (`LateApologySettings`). دکمه و برگهٔ رابطِ امروز (`OrderEvidenceEntry`) بی‌راه
 *  بودند و برداشته شدند (`claude/e-rows-39m`)؛ حالت و نوشتن همچنان در `OrderEvidenceController` (`OrderEvidenceState`).
 */

/** مبلغِ «کوچک‌ترین واحد» ⇒ متنِ پول (همان `money` ِ برنامه) */
internal fun evidenceMoney(minor: Long): String = ir.vil3ntec.tohid.money(minor / 100.0)
