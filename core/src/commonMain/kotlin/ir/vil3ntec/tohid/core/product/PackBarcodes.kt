package ir.vil3ntec.tohid.core.product

/*
 *  بارکدِ هر واحد — PRD-04 (§۶.۳): «چند بارکد برای هر کالا و **هر واحد**».
 *
 *  یک «بسته» (کارتن، شل، باکس، …) نام، اندازه به **واحدِ موجودیِ همان کالا** (به هزارم: کارتنِ ۲۴ دانه‌ای
 *  ⇒ 24000) و بارکدهای خودش را دارد. اسکنِ بارکدِ بسته یعنی «اندازهٔ بسته» از واحدِ موجودی به سبد می‌رود —
 *  همان ردیفِ کالا، همان قیمتِ واحد × اندازه (قیمتِ جدای بسته کارِ PRD-12 است).
 *
 *  • بارکد بین کالاها تکراری نمی‌شود (§۳.۴): بارکدِ بسته نه بارکدِ کالای دیگر است، نه بارکدِ بستهٔ کالای
 *    دیگر، نه بارکدِ خودِ همین کالا (وگرنه یک اسکن هم «۱» بود هم «۲۴»)، نه تکراری بینِ بسته‌ها.
 *  • اندازهٔ ۱ واحد همان بارکدِ کالاست ⇒ رد؛ اندازهٔ اعشاری برای کالای شمارشی («عدد» اعشار نمی‌گیرد) ⇒ رد.
 *  • جست‌وجو فقط **کامل** (و رقمِ فارسی/عربی ⇐ لاتین)؛ نیمه‌بارکد برای بسته نیست — ۲۴ دانهٔ اشتباه بدتر
 *    از «پیدا نشد» است.
 *  منطقِ خالص؛ آزمون: `PackBarcodesTest` (commonTest).
 */
object PackBarcodes {

  const val MAX_PACKS = 5
  const val MAX_NAME = 30
  /** یک میلیون واحد */
  const val MAX_SIZE_MILLI = 1_000_000_000L
  /** همهٔ بارکدهای بسته‌های یک کالا */
  const val MAX_CODES = ProductBarcodes.MAX

  data class Pack(val name: String, val sizeMilli: Long, val barcodes: List<String>)

  /** ورودیِ فرم: هر بسته یک ردیف با نام، اندازه و بارکدها (با «،»)؛ ردیفِ تمامِ خالی نادیده */
  data class Draft(val name: String = "", val size: String = "", val barcodes: String = "")

  sealed interface Checked {
    data class Ok(val packs: List<Pack>) : Checked
    data class Failed(val message: String) : Checked
  }

  fun draftOf(p: Pack): Draft = Draft(p.name, sizeText(p.sizeMilli), p.barcodes.joinToString("، "))

  /** 24000 ⇐ «24»، 250 ⇐ «0.25» */
  fun sizeText(milli: Long): String {
    val whole = milli / 1000
    val frac = milli % 1000
    return if (frac == 0L) whole.toString() else "$whole." + frac.toString().padStart(3, '0').trimEnd('0')
  }

  /**
   *  فرم ⇒ بسته‌ها، یا نخستین ایراد با پیامِ روشن.
   *  @param unit واحدِ موجودیِ کالا (برای پیام و «همان واحد»)
   *  @param fractional کالا اعشار می‌پذیرد؟ (`SalesEngine.isFractionalUnit` یا واحدِ فروشِ دوم)
   *  @param ownBarcodes بارکدهای خودِ همین کالا (کادرِ «بارکدها»)
   *  @param selfId شناسهٔ همین کالا؛ `null` برای کالای تازه
   *  @param ownerOf بارکد ⇐ شناسهٔ کالایی که دارد (بارکد یا بارکدِ بسته)؛ `null` یعنی آزاد
   */
  fun check(
    drafts: List<Draft>,
    unit: String,
    fractional: Boolean,
    ownBarcodes: List<String>,
    selfId: String?,
    ownerOf: (String) -> String?,
  ): Checked {
    val rows = drafts.filter { it.name.isNotBlank() || it.size.isNotBlank() || it.barcodes.isNotBlank() }
    if (rows.size > MAX_PACKS) return Checked.Failed("حداکثر $MAX_PACKS بسته برای یک کالا")
    val out = ArrayList<Pack>()
    val names = HashSet<String>()
    val seen = HashSet<String>()
    val own = ownBarcodes.toSet()
    for (r in rows) {
      val name = r.name.trim().replace(Regex("\\s+"), " ")
      if (name.isEmpty()) return Checked.Failed("نامِ بسته را بنویسید (مثلاً کارتن)")
      if (name.length > MAX_NAME) return Checked.Failed("نامِ بسته بیش از $MAX_NAME نویسه است")
      if (unit.isNotBlank() && SimilarNames.exactKey(name) == SimilarNames.exactKey(unit)) {
        return Checked.Failed("«$name» همان واحدِ کالاست — بارکدش را در «بارکدها» بنویسید")
      }
      if (!names.add(SimilarNames.exactKey(name))) return Checked.Failed("بستهٔ «$name» دو بار آمده")
      val size = DecimalText.scaled(r.size, 3)
        ?: return Checked.Failed("اندازهٔ بستهٔ «$name» را با عدد بنویسید (چند ${unit.ifBlank { "واحد" }})")
      if (size <= 0) return Checked.Failed("اندازهٔ بستهٔ «$name» باید بیش از صفر باشد")
      if (size == 1000L) return Checked.Failed("بستهٔ «$name» یک ${unit.ifBlank { "واحد" }} است — بارکدش را در «بارکدها» بنویسید")
      if (size > MAX_SIZE_MILLI) return Checked.Failed("اندازهٔ بستهٔ «$name» بیش از حد بزرگ است")
      if (!fractional && size % 1000 != 0L) {
        return Checked.Failed("«${unit.ifBlank { "عدد" }}» اعشار نمی‌گیرد — اندازهٔ بستهٔ «$name» عددِ درست باشد")
      }
      val codes = ProductBarcodes.parse(r.barcodes)
      if (codes.isEmpty()) return Checked.Failed("بستهٔ «$name» بارکد ندارد")
      for (c in codes) {
        if (c.length > 64) return Checked.Failed("بارکد بیش از ۶۴ نویسه است")
        if (c in own) return Checked.Failed("بارکدِ $c هم بارکدِ خودِ کالاست و هم بارکدِ بستهٔ «$name»")
        if (!seen.add(c)) return Checked.Failed("بارکدِ $c دو بار در بسته‌ها آمده")
        val owner = ownerOf(c)
        if (owner != null && owner != selfId) return Checked.Failed("بارکدِ $c قبلاً برای کالای دیگری ثبت شده")
      }
      out += Pack(name, size, codes)
    }
    if (seen.size > MAX_CODES) return Checked.Failed("حداکثر $MAX_CODES بارکد برای بسته‌های یک کالا")
    return Checked.Ok(out)
  }

  /** بارکدِ بسته ⇐ کالا و بسته */
  data class Hit(val productId: String, val pack: Pack)

  /** نتیجهٔ `Index.lookup` */
  sealed interface Lookup {
    data class Pack(val hit: Hit) : Lookup
    /** هم بارکدِ بسته و هم بارکدِ کالا — نه افزودن، پیامِ روشن */
    data object Ambiguous : Lookup
    data object NotPack : Lookup
  }

  /**
   *  جست‌وجوی اسکن: کامل، سپس با رقمِ لاتین. دو کالا با یک بارکدِ بسته (تعارضِ آفلاین) ⇒ هیچ‌کدام
   *  (`find` ⇐ null و `ambiguous`)، تا اسکن کالای اشتباه را به سبد نبرد.
   */
  class Index(products: List<Pair<String, List<Pack>>>) {
    private val hits = HashMap<String, Hit>()
    private val clash = HashSet<String>()

    init {
      for ((id, packs) in products) for (p in packs) for (c in p.barcodes) {
        for (k in setOf(c, latin(c))) {
          val prev = hits[k]
          if (prev != null && prev.productId != id) clash += k else if (prev == null) hits[k] = Hit(id, p)
        }
      }
    }

    val isEmpty: Boolean get() = hits.isEmpty()

    fun find(code: String): Hit? {
      val raw = code.trim()
      if (raw.isEmpty()) return null
      for (k in listOf(raw, latin(raw))) {
        if (k in clash) return null
        hits[k]?.let { return it }
      }
      return null
    }

    fun ambiguous(code: String): Boolean = code.trim().let { it in clash || latin(it) in clash }

    /**
     *  اسکن در صندوق: بسته، مبهم، یا «بسته نیست» (راهِ بارکدِ کالا). کدی که هم بارکدِ بسته است و هم
     *  کاملاً بارکدِ کالا (`isItemCode`، همان `barcodeIndex` — نه نیمه؛ تعارضِ آفلاین) ⇒ `Ambiguous`:
     *  هیچ‌کدام به سبد نمی‌رود، چون ۱ یا ۲۴ دانهٔ اشتباه پولِ اشتباه از مشتری می‌گیرد.
     */
    fun lookup(code: String, isItemCode: (String) -> Boolean): Lookup {
      val hit = find(code) ?: return Lookup.NotPack
      return if (isItemCode(code.trim())) Lookup.Ambiguous else Lookup.Pack(hit)
    }

    /** همهٔ بارکدهای بسته ⇐ شناسهٔ کالا (برای سنجشِ تکرار در ثبت و ویرایش) */
    fun owners(): Map<String, String> = hits.mapValues { it.value.productId }
  }

  /** «کارتن (24 دانه)» */
  fun label(p: Pack, unit: String): String = "${p.name} (${sizeText(p.sizeMilli)}${if (unit.isBlank()) "" else " $unit"})"

  private fun latin(s: String): String = buildString {
    for (ch in s) when (ch) {
      in '۰'..'۹' -> append('0' + (ch - '۰'))
      in '٠'..'٩' -> append('0' + (ch - '٠'))
      else -> append(ch)
    }
  }
}
