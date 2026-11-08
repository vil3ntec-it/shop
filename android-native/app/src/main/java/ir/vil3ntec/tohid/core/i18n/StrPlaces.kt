package ir.vil3ntec.tohid.core.i18n

/**
 *  محل‌های نگه‌داری و انتقال (INV-20) و بسته‌بندیِ دوباره و ساختِ بسته (INV-21) — دکمه‌ها و پنجره‌های انبار و کلیدِ «تنظیمات».
 *
 *  قاعده‌ها سرِ `I18n.kt`. ⛔ یادداشتِ ردیف‌های دفتر («شکستنِ بسته — …»، «انتقال بین محل‌ها») و پیامِ ردِ قاعده
 *  (`StockPlaces`، `Repack`) داده‌اند و همان فارسی می‌مانند.
 */
@Suppress("unused")
object StrPlaces {

  val on: String get() = t("روشن", "On", "روښانه")
  val off: String get() = t("خاموش", "Off", "مړ")
  val settingsOn: String get() = t("روشن — دکمه‌ها در انبار", "On — buttons in Inventory", "روښانه — تڼۍ په زېرمه کې")
  val settingsNote: String get() = t(
    "فقط روی همین دستگاه. خاموش ⇒ انبار، موجودی، خروجی و همگام‌سازی دقیقاً همان امروز.",
    "This device only. Off ⇒ inventory, stock, exports and sync stay exactly as today.",
    "یوازې په همدې وسیله. مړ ⇒ زېرمه، موجودي، صادرات او همغږي لکه نن.",
  )
  val placesSection: String get() = t("محل‌های نگه‌داری و انتقال", "Storage places and transfers", "د ساتنې ځایونه او لېږد")
  val placesSectionNote: String get() = t(
    "قفسه یا انبارِ پشتی تعریف کنید، موجودیِ هر محل را ببینید و کالا را جابه‌جا کنید؛ موجودیِ کل عوض نمی‌شود.",
    "Define shelves or a back store, see stock per place and move goods; total stock does not change.",
    "المارۍ یا شاتنۍ زېرمه وټاکئ، د هر ځای موجودي وګورئ او توکي ولېږدوئ؛ ټوله موجودي نه بدلېږي.",
  )
  val repackSection: String get() = t("بسته‌بندیِ دوباره و ساختِ بسته", "Repack and assemble", "بیا بسته‌بندي او د بستې جوړول")
  val repackSectionNote: String get() = t(
    "کارتن را به دانه بشکنید یا از چند کالا بسته بسازید؛ بها با کالا می‌رود.",
    "Break a carton into units or build a bundle from parts; cost moves with the goods.",
    "کارتن په دانو ووېشئ یا له څو توکو بسته جوړه کړئ؛ بیه له توکي سره ځي.",
  )

  /** INV-21 — «دستورِ ساخت و سریِ تولید» (`StockPlacesPref.bom`) */
  val bomSection: String get() = t("دستورِ ساخت و سریِ تولید", "Recipes and production lots", "د جوړولو لارښود او د تولید لړۍ")
  val bomSectionNote: String get() = t(
    "دستورِ ساخت برای هر کالا ثبت کنید و از آن بسازید؛ اجزا به ترتیبِ انقضا (یا سریِ برگزیده) برداشته می‌شوند، کالای ساخته‌شده زودترین انقضای اجزا را می‌گیرد و بهایش جمعِ بهای میانگینِ اجزاست. خاموش ⇒ همان بسته‌بندیِ امروز.",
    "Save a recipe per product and build from it; parts are taken by expiry (or a chosen lot), the product takes the earliest part expiry and its cost is the sum of the parts' average cost. Off ⇒ repacking stays as today.",
    "د هر توکي لپاره د جوړولو لارښود ثبت کړئ او له هغه جوړ کړئ؛ برخې د پای نېټې په ترتیب (یا ټاکلې لړۍ) اخیستل کېږي، جوړ شوی توکی د برخو تر ټولو ژره پای نېټه اخلي او بیه یې د برخو د منځنۍ بیې ټولګه ده. مړ ⇒ بسته‌بندي لکه نن.",
  )
  val bomTitle: String get() = t("دستورِ ساخت", "Recipe", "د جوړولو لارښود")
  val bomPick: String get() = t("ساختن از دستور", "Build from a recipe", "له لارښود څخه جوړول")
  val bomNone: String get() = t("هنوز دستوری ثبت نشده است", "No recipe saved yet", "تر اوسه هېڅ لارښود نه دی ثبت شوی")
  val bomSave: String get() = t("ثبتِ همین اجزا به‌عنوانِ دستور", "Save these parts as the recipe", "دا برخې د لارښود په توګه ثبت کړئ")
  val bomRemove: String get() = t("برداشتنِ دستور", "Remove the recipe", "لارښود لرې کول")
  val bomSaved: String get() = t("دستورِ ساخت ثبت شد", "Recipe saved", "لارښود ثبت شو")
  val bomRemoved: String get() = t("دستورِ ساخت برداشته شد", "Recipe removed", "لارښود لرې شو")
  val bomLoss: String get() = t("اتلاف (درصد)", "Expected loss (%)", "ضایع (سلنه)")
  fun bomLine(parts: Any, loss: Any): String = t(
    "${asIs(parts)} جزء، اتلاف ٪${asIs(loss)}", "$parts parts, loss $loss%", "$parts برخې، ضایع $loss٪",
  )
  fun lotsOut(text: Any): String = t("برداشت: ${asIs(text)}", "Taken: $text", "اخیستل: $text")

  /** POS-16 — سرخطِ «موجودی در محل‌ها» در برگهٔ ردیفِ سبدِ صندوق */
  val atTill: String get() = t("موجودی در محل‌ها", "Stock by place", "موجودي په ځایونو کې")

  val openPlaces: String get() = t("محل‌ها و انتقال", "Places and transfers", "ځایونه او لېږد")
  val openRepack: String get() = t("بسته‌بندیِ دوباره و ساختِ بسته", "Repack and assemble", "بیا بسته‌بندي او د بستې جوړول")

  val placesTitle: String get() = t("محل‌های نگه‌داری", "Storage places", "د ساتنې ځایونه")
  val placesNote: String get() = t(
    "انتقال فقط جای کالا را عوض می‌کند؛ موجودیِ کل همان می‌ماند.",
    "A transfer only changes where goods are; total stock stays the same.",
    "لېږد یوازې د توکي ځای بدلوي؛ ټوله موجودي همغسې پاتې کېږي.",
  )
  val newPlace: String get() = t("نامِ محلِ تازه، مثلاً انبارِ پشتی", "New place name, e.g. back store", "د نوي ځای نوم، لکه شاتنۍ زېرمه")
  val add: String get() = t("افزودن", "Add", "زیاتول")
  val archive: String get() = t("بایگانی", "Archive", "آرشیف")
  fun items(n: Any): String = t("${asIs(n)} کالا", "$n items", "$n توکي")
  val transferTitle: String get() = t("انتقال بین محل‌ها", "Move between places", "د ځایونو ترمنځ لېږد")
  val search: String get() = t("نامِ کالا…", "Product name…", "د توکي نوم…")
  val otherProduct: String get() = t("کالای دیگر", "Other product", "بل توکی")
  val from: String get() = t("از", "From", "له")
  val to: String get() = t("به", "To", "ته")
  val origin: String get() = t("مبدأ", "Origin", "سرچینه")
  val destination: String get() = t("مقصد", "Destination", "منزل")
  val amount: String get() = t("مقدار", "Quantity", "اندازه")
  val reason: String get() = t("دلیل (اختیاری)", "Reason (optional)", "دلیل (اختیاري)")
  val transfer: String get() = t("انتقال", "Move", "لېږد")
  val noStock: String get() = t("این کالا در هیچ محلی موجودی ندارد", "This product has no stock in any place", "دا توکی په هېڅ ځای کې موجودي نه لري")

  // ── سری و انقضا در انتقال (INV-20) ──
  val lotsHere: String get() = t("سری‌ها در مبدأ", "Lots at the origin", "په سرچینه کې لړۍ")
  val byFefo: String get() = t("اول آنچه زودتر منقضی می‌شود", "Earliest expiry first", "لومړی هغه چې ژر یې نېټه تېرېږي")
  val byLot: String get() = t("انتخابِ سری", "Choose lots", "لړۍ وټاکئ")
  fun expires(date: Any): String = t("انقضا ${asIs(date)}", "Expires $date", "د تېرېدو نېټه $date")
  val noExpiry: String get() = t("بی انقضا", "No expiry", "بې نېټې")
  fun moves(list: Any): String = t("جابه‌جا می‌شود: ${asIs(list)}", "Moves: $list", "لېږدول کېږي: $list")
  fun total(q: Any, unit: Any): String = t("جمع: ${asIs(q)} ${asIs(unit)}", "Total: $q $unit", "ټول: $q $unit")
  val lotlessHint: String get() = t(
    "کالای بی سری فقط با «اول آنچه زودتر منقضی می‌شود» و پس از همهٔ سری‌ها جابه‌جا می‌شود.",
    "Stock without a lot moves only with “earliest expiry first”, after every lot.",
    "بې لړۍ توکی یوازې د «لومړی هغه چې ژر یې نېټه تېرېږي» سره او له ټولو لړیو وروسته لېږدول کېږي.",
  )
  /** گزارشِ انقضا: محل‌هایی که همان محموله حالا در آن‌هاست */
  fun inPlaces(names: Any): String = t("در ${asIs(names)}", "At $names", "په $names کې")
  val close: String get() = t("بستن", "Close", "بندول")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")

  val moved: String get() = t("انتقال ثبت شد", "Transfer saved", "لېږد ثبت شو")
  val placeAdded: String get() = t("محل افزوده شد", "Place added", "ځای زیات شو")
  val placeArchived: String get() = t("محل بایگانی شد", "Place archived", "ځای آرشیف شو")
  val again: String get() = t("همین کار پیش‌تر ثبت شده بود", "This was already saved", "دا کار مخکې ثبت شوی و")
  val notSaved: String get() = t("ثبت نشد", "Not saved", "ثبت نه شو")

  val repackTitle: String get() = t("بسته‌بندیِ دوباره و ساختِ بسته", "Repack and assemble", "بیا بسته‌بندي او د بستې جوړول")
  val modeBreak: String get() = t("شکستنِ کارتن", "Break a carton", "د کارتن ماتول")
  val modeBuild: String get() = t("ساختِ بسته", "Build a bundle", "د بستې جوړول")
  val breakNote: String get() = t(
    "کالای کارتن کم و کالای دانه زیاد می‌شود؛ بهای هر دانه = بهای کارتن ÷ دانه‌ها.",
    "The carton product goes down and the unit product up; unit cost = carton cost ÷ units.",
    "د کارتن توکی کمېږي او د دانې توکی زیاتېږي؛ د هرې دانې بیه = د کارتن بیه ÷ دانې.",
  )
  val buildNote: String get() = t(
    "اجزا کم و بسته زیاد می‌شود؛ بهای بسته = جمعِ بهای اجزا.",
    "The parts go down and the bundle up; bundle cost = sum of the parts.",
    "برخې کمېږي او بسته زیاتېږي؛ د بستې بیه = د برخو د بیو ټولګه.",
  )
  val packProduct: String get() = t("کالای کارتن", "Carton product", "د کارتن توکی")
  val unitProduct: String get() = t("کالای دانه", "Unit product", "د دانې توکی")
  val bundleProduct: String get() = t("کالای بسته", "Bundle product", "د بستې توکی")
  val packs: String get() = t("تعدادِ کارتن", "Cartons", "د کارتنونو شمېر")
  val perPack: String get() = t("دانه در هر کارتن", "Units per carton", "په هر کارتن کې دانې")
  val count: String get() = t("تعدادِ بسته", "Bundles", "د بستو شمېر")
  val parts: String get() = t("اجزای یک بسته", "Parts of one bundle", "د یوې بستې برخې")
  val addPart: String get() = t("+ جزء", "+ Part", "+ برخه")
  val remove: String get() = t("حذف", "Remove", "لرې کول")
  val place: String get() = t("محل", "Place", "ځای")
  val note: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val save: String get() = t("ثبت", "Save", "ثبت")
  fun result(q: Any, unit: Any, name: Any, cost: Any): String =
    t("⇐ ${asIs(q)} ${asIs(unit)} «${asIs(name)}»، بهای هر واحد ${asIs(cost)} ؋", "⇒ $q $unit “$name”, unit cost $cost ؋", "⇐ $q $unit «$name»، د هر واحد بیه $cost ؋")
  fun need(q: Any, unit: Any, name: Any, have: Any): String =
    t("${asIs(q)} ${asIs(unit)} «${asIs(name)}» (موجود: ${asIs(have)})", "$q $unit “$name” (in stock: $have)", "$q $unit «$name» (موجود: $have)")
  val savedRepack: String get() = t("ثبت شد", "Saved", "ثبت شو")
}
