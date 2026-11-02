package ir.vil3ntec.tohid.core.courier

/**
 *  DLV-05 — پیوندهای مسیریابِ پینِ مقصد، فقط همان که سرور در کارتِ مأموریت می‌دهد (`navigation` ِ
 *  `courier-map.decorate`، پرچمِ `shop.delivery_map`؛ خاموش ⇒ کلید نیست ⇒ null و دکمه همان رفتارِ پیشین).
 *
 *  ⛔ فقط مختصات: هیچ نشانه، نام یا شماره‌ای به برنامهٔ بیرونی نمی‌رود. پیوندی که دقیقاً به قالبِ سرور
 *     (`geo:`، Google Maps، OsmAnd) نباشد دور ریخته می‌شود — پاسخِ دست‌کاری‌شده برنامهٔ دیگری باز نمی‌کند.
 */
data class MissionNav(val geo: String = "", val google: String = "", val osmand: String = "") {

  /** به ترتیب: مسیریابِ خودِ گوشی (هر برنامهٔ نقشه، آفلاین هم)، OsmAnd (نقشهٔ آفلاین)، Google Maps */
  val links: List<String> get() = listOf(geo, osmand, google).filter { it.isNotEmpty() }

  companion object {
    private const val N = """-?\d{1,3}(?:\.\d{1,6})?"""
    private val GEO = Regex("""^geo:$N,$N\?q=$N,$N$""")
    private val GOOGLE = Regex("""^https://www\.google\.com/maps/dir/\?api=1&destination=$N,$N$""")
    private val OSMAND = Regex("""^https://osmand\.net/map\?pin=$N,$N#\d{1,2}/$N/$N$""")

    /** پیوندهای سرور ⇐ فقط آن‌ها که قالبِ درست دارند؛ هیچ ⇒ null */
    fun of(geo: String?, google: String?, osmand: String?): MissionNav? {
      val nav = MissionNav(
        geo = geo.orEmpty().takeIf { GEO.matches(it) }.orEmpty(),
        google = google.orEmpty().takeIf { GOOGLE.matches(it) }.orEmpty(),
        osmand = osmand.orEmpty().takeIf { OSMAND.matches(it) }.orEmpty(),
      )
      return nav.takeIf { it.links.isNotEmpty() }
    }
  }
}
