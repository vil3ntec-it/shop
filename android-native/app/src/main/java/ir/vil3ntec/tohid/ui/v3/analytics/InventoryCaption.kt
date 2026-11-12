package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.i18n.StrReports

/** ۳-۱۶ (۲): زیرِ «ارزش موجودی» — بهای میانگینِ موزون؛ تقریبی، کالاهای منفی و کیت‌ها (هر کدام فقط اگر هست) */
internal fun inventoryCaption(approx: Boolean, negative: Int, kits: Int): String = listOfNotNull(
  StrReports.inventoryAvgCost,
  if (approx) StrReports.inventoryApprox else null,
  if (negative > 0) StrReports.inventoryNegative(negative) else null,
  if (kits > 0) StrReports.inventoryKits(kits) else null,
).joinToString(" · ")
