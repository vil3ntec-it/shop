package ir.vil3ntec.tohid.core.i18n

/**
 *  سفارش، جمع‌آوری و ارسال در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ هنوز صدا می‌زند: برگشتِ کالای سفارشِ بازپرداخت‌شده
 *  (`OrderRefundUi`)، دستورِ تحویل (`OrderDeliveryPrefsUi`)، جایگزین (`OrderSubstitutionUi`)، برگهٔ جمع‌آوری (`PickSheet`)،
 *  اصلاحِ نشانی (`DeliveryAddressFix`)، گزارشِ نقدِ پیک‌ها (`DeliveryCashReport`) و QR ِ تحویل (`HandoverQr`).
 *  این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند. فارسی کلمه‌به‌کلمه همان پیشین است. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrOrderPages {
  // ── `OrderRefundUi.kt` ──
  val restockAsk: String get() = t("کالا به انبار برگشت؟", "Did the goods go back to the warehouse?", "توکي بېرته ګدام ته راغلل؟")
  val restockNote: String get() = t(
    "فقط قلمی را بزنید که مشتری واقعاً پس آورد؛ بقیه پیشِ مشتری می‌ماند و موجودی دست نمی‌خورد.",
    "Tap only the items the customer actually brought back; the rest stays with the customer and stock is not touched.",
    "یوازې هغه توکي وټاکئ چې پېرودونکي رښتیا بېرته راوړل؛ نور له پېرودونکي سره پاتې کېږي او موجودي نه بدلېږي.",
  )
  fun restockTitle(no: Any?): String = t("برگشتِ کالا — سفارش #${asIs(no)}", "Goods return — order #$no", "د توکو ستنېدنه — فرمایش #$no")
  val restockNone: String get() = t("هیچ کالایی برنگشت", "No goods came back", "هېڅ توکی بېرته رانغی")
  val restockDo: String get() = t("به انبار برگرداند", "Put back in the warehouse", "ګدام ته بېرته ورکړئ")
  fun restockPending(n: Any?): String = t(
    "${asIs(n)} سفارشِ بازپرداخت‌شده: کالا به انبار برگشت؟",
    "$n refunded orders: did the goods go back to the warehouse?",
    "$n بېرته ورکړل شوي فرمایشونه: توکي بېرته ګدام ته راغلل؟",
  )
  fun refundLine(order: Any?, invoice: Any?): String = t(
    "سفارش #${asIs(order)} — فاکتور #${asIs(invoice)}",
    "Order #$order — invoice #$invoice",
    "فرمایش #$order — بل #$invoice",
  )
  fun refundUnposted(n: Any?): String = t(
    "${asIs(n)} بازپرداخت هنوز در دفترِ دکان ننشسته — گزارش و صندوق تا آن‌وقت بیش از واقع",
    "$n refunds are not in the shop's books yet — reports and till show more than real until then",
    "$n بېرته ورکړې لا د دوکان په دفتر کې نه دي ثبت — تر هغه راپور او صندوق تر رښتیا ډېر ښيي",
  )
  val restockPosted: String get() = t(
    "برگشتِ کالا؟ — بازپرداختِ این سفارش در دفترِ دکان ثبت شد",
    "Return the goods? — this order's refund was recorded in the shop's books",
    "توکي بېرته؟ — د دې فرمایش بېرته ورکړه د دوکان په دفتر کې ثبت شوه",
  )
  val restockPostedNote: String get() = t(
    "کالایی که مشتری پس آورد به انبار برمی‌گردد؛ آن‌چه پیشِ مشتری ماند نه. تا جواب ندهید موجودی دست نمی‌خورد.",
    "Goods the customer brought back go back to the warehouse; what stayed with the customer does not. Stock is not touched until you answer.",
    "هغه توکي چې پېرودونکي بېرته راوړل ګدام ته ورګرځي؛ هغه چې له پېرودونکي سره پاتې شول نه. تر ځواب پورې موجودي نه بدلېږي.",
  )
  val restockAction: String get() = t("برگشتِ کالا…", "Goods return…", "د توکو ستنېدنه…")
  val refundNotPosted: String get() = t(
    "بازپرداختِ این سفارش هنوز در دفترِ دکان ننشسته",
    "This order's refund is not in the shop's books yet",
    "د دې فرمایش بېرته ورکړه لا د دوکان په دفتر کې نه ده ثبت",
  )
  fun refundPosted(no: Any?): String = t(
    "بازپرداختِ سفارش #${asIs(no)} در دفترِ دکان ثبت شد (سندِ برگشت)؛ گزارش و صندوق درست شدند",
    "Refund for order #$no recorded in the shop's books (return voucher); reports and till are now right",
    "د #$no فرمایش بېرته ورکړه د دوکان په دفتر کې ثبت شوه (د ستنېدنې سند)؛ راپور او صندوق سم شول",
  )
  val restockNoneSaved: String get() = t(
    "ثبت شد: کالایی به انبار برنگشت",
    "Recorded: no goods went back to the warehouse",
    "ثبت شو: هېڅ توکی ګدام ته بېرته لاړ نه شو",
  )
  val restockSaved: String get() = t(
    "کالای زده‌شده به انبار برگشت و همگام می‌شود",
    "The chosen goods went back to the warehouse and will sync",
    "ټاکل شوي توکي ګدام ته بېرته لاړل او همغږي کېږي",
  )

  // ── `OrderDeliveryPrefsUi.kt` ──
  fun prefLandmark(x: Any?): String = t("نشانه: ${asIs(x)}", "Landmark: $x", "نښه: $x")
  fun prefPin(lat: Any?, lng: Any?): String = t("پین: ${asIs(lat)}, ${asIs(lng)}", "Pin: $lat, $lng", "پن: $lat, $lng")
  val prefCallFirst: String get() = t("پیش از رسیدن زنگ بزنید", "Call before arriving", "تر رسېدو مخکې زنګ ووهئ")
  fun prefLeaveWith(x: Any?): String = t("تحویل به: ${asIs(x)}", "Hand to: $x", "سپارل: $x")
  val prefEdit: String get() = t("ویرایشِ دستورِ تحویل", "Edit delivery instructions", "د سپارلو لارښوونه سمول")
  val prefTenderOpt: String get() = t(
    "مشتری با چه مبلغی می‌پردازد؟ (افغانی، اختیاری)",
    "What amount will the customer pay with? (AFN, optional)",
    "پېرودونکی په څومره پیسو تادیه کوي؟ (افغانۍ، اختیاري)",
  )
  val prefTenderNote: String get() = t(
    "تا پیک پول خرد بیاورد؛ مثلاً ۱۰۰۰",
    "So the courier brings change; e.g. 1000",
    "چې سپارونکی ماتې پیسې راوړي؛ لکه ۱۰۰۰",
  )
  fun prefTitle(no: Any?): String = t("دستورِ تحویل — سفارش #${asIs(no)}", "Delivery instructions — order #$no", "د سپارلو لارښوونه — فرمایش #$no")
  val prefLeaveWithField: String get() = t("تحویل به (عضوِ خانواده، دربان…)", "Hand to (family member, doorkeeper…)", "سپارل (د کورنۍ غړي، دربان…)")
  val prefTender: String get() = t(
    "مشتری با چه مبلغی می‌پردازد؟ (افغانی)",
    "What amount will the customer pay with? (AFN)",
    "پېرودونکی په څومره پیسو تادیه کوي؟ (افغانۍ)",
  )
  val saving: String get() = t("در حالِ ذخیره…", "Saving…", "خوندي کېږي…")

  // ── `OrderSubstitutionUi.kt` ──
  val subIfMissing: String get() = t("اگر کالایی نبود", "If an item is out", "که کوم توکی نه و")

  // ── `HandoverQr.kt` ──
  fun handoverQr(no: Any?): String = t("QRِ کدِ سفارش #${asIs(no)}", "QR of the order code #$no", "د فرمایش د کوډ QR #$no")

  // ── `PickSheet.kt` ──
  fun pickRetry(msg: Any?): String = t(
    "${asIs(msg)} — دوباره بزنید؛ همان کار یک بار حساب می‌شود",
    "$msg — tap again; the same action counts once",
    "$msg — بیا یې ووهئ؛ همغه کار یو ځل حسابېږي",
  )
  fun pickTitle(no: Any?): String = t("جمع‌آوری — سفارش #${asIs(no)}", "Picking — order #$no", "راټولول — فرمایش #$no")
  fun pickProgress(done: Any?, total: Any?): String = t("${asIs(done)} از ${asIs(total)} قلم", "$done of $total lines", "$done له $total توکو")
  val pickPacked: String get() = t("بسته‌بندی‌شده", "Packed", "بسته بندي شوی")
  val pickPicking: String get() = t("در حال جمع‌آوری", "Picking", "راټولېږي")
  fun pickNotPicking(status: Any?): String = t(
    "سفارش دیگر «در حال جمع‌آوری» نیست (${asIs(status)}).",
    "The order is no longer \"picking\" ($status).",
    "فرمایش نور «راټولېږي» نه دی ($status).",
  )
  fun pickScanField(name: Any?): String = t("اسکن یا بارکد — «${asIs(name)}»", "Scan or barcode — \"$name\"", "سکن یا بارکوډ — «$name»")
  fun pickPlaceSaved(name: Any?): String = t(
    "جای «${asIs(name)}» برای جمع‌آوری‌های بعدی ثبت شد",
    "Place of \"$name\" saved for later picks",
    "د «$name» ځای د راتلونکو راټولولو لپاره ثبت شو",
  )
  fun pickFinalTotal(amount: Any?): String = t("جمعِ نهایی: ${asIs(amount)}", "Final total: $amount", "وروستۍ ټولګه: $amount")
  fun pickTotalSoFar(amount: Any?): String = t("جمعِ تا این‌جا: ${asIs(amount)}", "Total so far: $amount", "تر اوسه ټولګه: $amount")
  fun pickDeducted(orig: Any?, cut: Any?): String = t(
    "از ${asIs(orig)} ِ سفارش ${asIs(cut)} کم می‌شود (قلمِ پیدانشده یا کم) — نقدِ صندوق و پیک همین جمعِ تازه است.",
    "$cut is taken off the order's $orig (item not found or short) — till and courier cash use this new total.",
    "د فرمایش له $orig څخه $cut کمېږي (نه موندل شوی یا کم توکی) — د صندوق او سپارونکي نغدې همدا نوې ټولګه ده.",
  )
  //  تصمیمِ ۱-۶ ب: سفارشِ پرداخت‌شده با کسر — دکان پول را خودش پس می‌دهد و «پس داده شد» می‌زند؛ سرور ثبت می‌کند
  val pickRefundNote: String get() = t(
    "این سفارش پرداخت شده است. مبلغِ کم‌شده را خودتان به مشتری پس بدهید، بعد یکی را بزنید؛ مبلغ را سرور حساب و ثبت می‌کند.",
    "This order is paid. Give the customer back the amount taken off yourself, then tap one; the server works out and records the amount.",
    "دا فرمایش تادیه شوی دی. کمه شوې پیسې پخپله پېرودونکي ته بېرته ورکړئ، بیا یو یې ووهئ؛ مبلغ سرور حسابوي او ثبتوي.",
  )
  val pickRefundCash: String get() = t("نقد پس دادم", "Paid back in cash", "نغدې مې بېرته ورکړې")
  val pickRefundHesab: String get() = t("با حساب‌پی پس دادم", "Paid back by HesabPay", "په حساب‌پې مې بېرته ورکړې")
  val pickRefundDone: String get() = t(
    "بسته‌بندی شد و بازپرداخت ثبت شد؛ برچسبِ کیسه‌ها پایین است",
    "Packed and the refund was recorded; bag labels are below",
    "بسته بندي شو او بېرته ورکړه ثبت شوه؛ د کڅوړو لیبلونه لاندې دي",
  )
  val pickBags: String get() = t("کیسه‌ها", "Bags", "کڅوړې")
  fun pickLeft(n: Any?): String = t("${asIs(n)} قلم مانده", "$n lines left", "$n توکي پاتې")
  val pickPackedDone: String get() = t("بسته‌بندی شد", "Packed", "بسته بندي شو")
  val pickPackedLabels: String get() = t(
    "بسته‌بندی شد؛ برچسبِ کیسه‌ها پایین است",
    "Packed; bag labels are below",
    "بسته بندي شو؛ د کڅوړو لیبلونه لاندې دي",
  )
  val pickBagLabels: String get() = t("برچسبِ کیسه‌ها", "Bag labels", "د کڅوړو لیبلونه")
  fun pickBagLabel(no: Any?, bag: Any?, of: Any?): String = t(
    "#${asIs(no)} · کیسهٔ ${asIs(bag)} از ${asIs(of)}",
    "#$no · bag $bag of $of",
    "#$no · کڅوړه $bag له $of څخه",
  )
  fun pickLineQty(ordered: Any?, picked: Any?): String = t(
    "سفارش: ${asIs(ordered)} · برداشته: ${asIs(picked)}",
    "Ordered: $ordered · picked: $picked",
    "فرمایش: $ordered · اخیستل شوی: $picked",
  )
  fun pickWrongScans(n: Any?): String = t(" · اسکنِ اشتباه: ${asIs(n)}", " · wrong scans: $n", " · ناسم سکنونه: $n")
  fun pickWeight(unit: Any?): String = t("وزنِ واقعی (${asIs(unit)})", "Actual weight ($unit)", "اصلي وزن ($unit)")
  val pickWeightHint: String get() = t(
    "اول کالا را اسکن کنید، سپس وزنِ ترازو",
    "Scan the item first, then the scale weight",
    "لومړی توکی سکن کړئ، بیا د تلې وزن",
  )
  val pickSaveWeight: String get() = t("ثبتِ وزن", "Save weight", "وزن ثبت کړئ")
  val pickTookNoCode: String get() = t("برداشتم (بی بارکد)", "Picked (no barcode)", "واخیست (بې بارکوډ)")
  val pickRestart: String get() = t("از نو", "Start over", "له سره")
  val pickPlaceClose: String get() = t("بستنِ جا", "Close place", "ځای وتړئ")
  val pickPlaceShelf: String get() = t("جای قفسه", "Shelf place", "د الماري ځای")
  val pickPlaceSave: String get() = t("ثبتِ جا", "Save place", "ځای ثبت کړئ")
  val kilo: String get() = t("کیلو", "kg", "کیلو")

  // ── `DeliveryCashReport.kt` ──
  val cashDeliveries: String get() = t("تحویلِ نقدی", "Cash deliveries", "نغدې سپارنې")
  val cashCollected: String get() = t("نقدِ گرفته از مشتری", "Cash taken from customers", "له پېرودونکو اخیستل شوې نغدې")
  val cashCustomerDiff: String get() = t("اختلافِ مشتری", "Customer difference", "د پېرودونکي توپیر")
  val cashSettledPart: String get() = t("از آن تسویه‌شده", "Of which settled", "له هغې تصفیه شوې")
  val cashUnsettledPart: String get() = t("از آن تسویه‌نشده", "Of which unsettled", "له هغې نه تصفیه شوې")
  fun cashSettlements(n: Any?): String = t("تسویه‌ها (${asIs(n)})", "Settlements ($n)", "تصفیې ($n)")
  fun cashSettleLine(due: Any?, got: Any?): String = t(
    "بدهی ${asIs(due)} · شمرده ${asIs(got)}",
    "Owed $due · counted $got",
    "پور $due · شمېرل شوي $got",
  )
  val cashShortage: String get() = t("کسریِ تسویه", "Settlement shortage", "د تصفیې کمښت")
  val cashExcess: String get() = t("اضافهٔ تسویه", "Settlement excess", "د تصفیې زیاتوالی")
  val cashInHand: String get() = t("در دست، همین حالا", "In hand right now", "همدا اوس په لاس کې")
  val cashBadRange: String get() = t("بازهٔ زمانی ساخته نشد", "The time range could not be built", "د وخت موده جوړه نه شوه")
  val cashTitle: String get() = t("نقدِ پیک‌ها و تسویه", "Couriers' cash and settlement", "د سپارونکو نغدې او تصفیه")
  val cashNoReport: String get() = t("گزارش نیامد", "The report did not come", "راپور رانغی")
  val cashConsistent: String get() = t("جمعِ پیک‌ها = جمعِ کل", "Couriers' total = grand total", "د سپارونکو ټولګه = ټوله ټولګه")
  val cashInconsistent: String get() = t(
    "جمعِ پیک‌ها با جمعِ کل نمی‌خواند؛ دوباره بگیرید",
    "Couriers' total does not match the grand total; fetch again",
    "د سپارونکو ټولګه له ټولې ټولګې سره سمون نه خوري؛ بیا یې راوړئ",
  )
  val cashServerTime: String get() = t(
    "زمانِ سرور؛ «در دست» مالِ همین لحظه است.",
    "Server time; \"in hand\" is for this very moment.",
    "د سرور وخت؛ «په لاس کې» د همدې شېبې دی.",
  )
  val cashAllCouriers: String get() = t("جمعِ همهٔ پیک‌ها", "All couriers' total", "د ټولو سپارونکو ټولګه")
  val cashNoFlow: String get() = t(
    "در این بازه گردشِ نقدی نیست و هیچ پیکی نقد در دست ندارد.",
    "No cash moved in this range and no courier holds cash.",
    "په دې موده کې نغدې نه دي چلېدلې او هېڅ سپارونکی نغدې په لاس کې نه لري.",
  )
  val cashNeedsLook: String get() = t("نیازمندِ نگاه", "Needs a look", "کتنې ته اړتیا")
  val cashOk: String get() = t("درست", "Correct", "سم")

  // ── `DeliveryAddressFix.kt` ──
  fun addrFixed(no: Any?): String = t(
    "سفارش #${asIs(no)}: نشانی اصلاح شد؛ پیک نشانیِ تازه را می‌بیند",
    "Order #$no: address corrected; the courier sees the new address",
    "فرمایش #$no: پته سمه شوه؛ سپارونکی نوې پته ویني",
  )
  val addrConflict: String get() = t(
    "این مأموریت همین حالا جای دیگری عوض شد؛ میز تازه شد.",
    "This job changed elsewhere just now; the desk was refreshed.",
    "دا دنده همدا اوس بل ځای بدله شوه؛ مېز تازه شو.",
  )
  fun addrTitle(action: Any?, no: Any?): String = t("${asIs(action)} — سفارش #${asIs(no)}", "$action — order #$no", "$action — فرمایش #$no")
  val addrAsk: String get() = t(
    "نشانیِ درست را از مشتری بپرسید. پیک اول نشانه را می‌بیند، بعد ناحیه و رنگِ دروازه.",
    "Ask the customer for the right address. The courier sees the landmark first, then the area and the gate colour.",
    "له پېرودونکي سمه پته وپوښتئ. سپارونکی لومړی نښه ویني، بیا سیمه او د دروازې رنګ.",
  )
  val addrLandmark: String get() = t("نشانهٔ نزدیک", "Nearby landmark", "نږدې نښه")
  val addrLandmarkEg: String get() = t("مثلاً «روبه‌روی نانوایی»", "e.g. \"opposite the bakery\"", "لکه «د نانوايي مخې ته»")
  val addrArea: String get() = t("ناحیه (اختیاری)", "Area (optional)", "سیمه (اختیاري)")
  val addrGate: String get() = t("رنگِ دروازه (اختیاری)", "Gate colour (optional)", "د دروازې رنګ (اختیاري)")
  val addrText: String get() = t("نشانی (اختیاری)", "Address (optional)", "پته (اختیاري)")
  val addrKeepPin: String get() = t("پینِ مشتری بماند", "Keep the customer's pin", "د پېرودونکي پن دې پاتې شي")
  val addrClearPin: String get() = t("پینِ قبلی پاک شود (پین غلط بود)", "Clear the old pin (the pin was wrong)", "زوړ پن دې پاک شي (پن ناسم و)")
  val addrNeedOne: String get() = t("نشانه یا نشانی لازم است.", "A landmark or address is required.", "نښه یا پته اړینه ده.")
}
