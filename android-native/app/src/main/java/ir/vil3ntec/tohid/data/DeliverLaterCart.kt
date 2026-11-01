package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.DeliverLater

/**
 *  POS-38 — سبدِ صندوق (`SalesEngine.CartLine`) ⇐ قلم‌های سفارشِ ارسال (`core/pos/DeliverLater`).
 *
 *  • کالای ثبت‌شده: شناسهٔ کالا، نامِ همان لحظه و **واحدِ موجودی** (`unit`): مقدار و قیمتِ سبد همیشه به
 *    همان واحد است (واحدِ فروشِ دوم فقط ورودیِ مقدار را تبدیل می‌کند، `Product.saleUnit`)، پس
 *    `OrderFulfillment.stockQty` در فروشِ تحویل همان مقدار را بی تبدیلِ دوباره کم می‌کند.
 *  • قلمِ آزاد: شناسهٔ ردیفِ آزاد کالا نیست ⇒ `productId` تهی (فروشِ تحویل قلمِ آزاد می‌سازد، بی اثر بر موجودی).
 *  • قیمت: همان قیمتِ ردیفِ سبد (`SalesEngine.linePrice`: دستی، تخفیفِ ردیف یا قیمتِ کالا)؛ صدازننده سبدِ
 *    `priced` (با تخفیفِ خودکار) را می‌دهد تا جمعِ سفارش همان جمعی باشد که صندوق نشان می‌دهد.
 *  • ردیفی که کالایش دیگر در دفتر نیست (و آزاد هم نیست) کنار می‌رود — همان رفتارِ سبدِ صندوق.
 */
object DeliverLaterCart {

  fun lines(d: ShopData, cart: List<SalesEngine.CartLine>): List<DeliverLater.Line> {
    val byId = d.products.associateBy { it.id }
    return cart.mapNotNull { l ->
      val price = DeliverLater.minor(SalesEngine.linePrice(d, l))
      if (l.free) return@mapNotNull DeliverLater.Line("", l.label, "", DeliverLater.milli(l.quantity), price)
      val p = byId[l.productId] ?: return@mapNotNull null
      DeliverLater.Line(p.id, p.name, p.unit, DeliverLater.milli(l.quantity), price)
    }
  }
}
