package ir.vil3ntec.tohid.core.keymap

/**
 *  ثبتِ مرکزیِ کلیدها — MASTER-SPEC ۳.۱۶.
 *
 *  `keymap.json` در همین پوشه تنها منبعِ میان‌برهاست؛ `gen-keymap.mjs` از آن
 *  `KeyMap.kt` (این‌جا) و `license/keymap.js` (وب) را می‌سازد. صفحه‌ها کلید را
 *  از `KeyMap` می‌گیرند (`ui/screens/KeyBinding.kt`)، نه با `Key.P`ِ دستی.
 *  `KeyMapTest` می‌پاید که فایلِ ساخته‌شده با json یکی باشد، در هیچ دامنه
 *  کلیدی دو بار نیامده باشد و هیچ کلیدی روی کلیدهای رزروِ سیستم یا مرورگر ننشیند.
 *
 *  Kotlinِ خالص است (بی Compose) تا با رفتنِ `core` به ماژولِ مشترک جابه‌جا شود.
 */

/** یک ترکیبِ کلید. `mod` = Ctrl در ویندوز و لینوکس و اندروید، Cmd در مک (`isPrimaryModifier`). */
data class Chord(
  val key: KeyName,
  val mod: Boolean = false,
  val shift: Boolean = false,
  val alt: Boolean = false,
) {
  /** برچسبِ کاربر، به ترتیبِ جدولِ ۵.۲ (Ctrl، Alt، Shift): «Ctrl+Shift+N»، «Alt+Shift+X»؛ روی مک «Cmd+Shift+N» */
  fun label(mac: Boolean = false): String = buildList {
    if (mod) add(if (mac) "Cmd" else "Ctrl")
    if (alt) add(if (mac) "Option" else "Alt")
    if (shift) add("Shift")
    add(key.label)
  }.joinToString("+")

  /** همان نوشتارِ keymap.json: «Mod+Shift+N» */
  override fun toString(): String = buildList {
    if (mod) add("Mod")
    if (alt) add("Alt")
    if (shift) add("Shift")
    add(key.name)
  }.joinToString("+")

  companion object {
    /** «Mod+Shift+F5» یا «Mod+1» ⇒ Chord؛ نامِ ناشناخته ⇒ IllegalArgumentException */
    fun parse(text: String): Chord {
      val parts = text.split('+')
      val last = parts.last()
      val name = if (last.length == 1 && last[0] in '0'..'9') "D$last" else last
      val key = KeyName.entries.firstOrNull { it.name == name } ?: throw IllegalArgumentException("کلیدِ ناشناخته «$last» در «$text»")
      var c = Chord(key)
      for (m in parts.dropLast(1)) {
        c = when {
          m == "Mod" && !c.mod -> c.copy(mod = true)
          m == "Shift" && !c.shift -> c.copy(shift = true)
          m == "Alt" && !c.alt -> c.copy(alt = true)
          else -> throw IllegalArgumentException("پیرایهٔ نادرست «$m» در «$text»")
        }
      }
      return c
    }
  }
}

/** live = موجود، partial = ناقص (همهٔ صفحه‌ها ندارند)، planned = جدید یا تغییرِ رفتار (هنوز کار نمی‌کند) */
enum class KeyStatus { LIVE, PARTIAL, PLANNED }

/** دامنه: سراسری، صفحه یا پنجره. کلیدِ پنجرهٔ باز بر صفحه و سراسری مقدم است. */
data class KeyScope(val id: String, val kind: Kind, val title: String, val parent: String? = null) {
  enum class Kind { GLOBAL, SCREEN, WINDOW }
}

/**
 *  یک کار و کلیدهایش.
 *  @param keys کلیدهای برنامهٔ کامپیوتر و اندرویدِ با کیبورد
 *  @param web کلیدهای مرورگر (ستونِ «وب»)؛ خالی = در وب کلید ندارد
 *  @param overrides کلیدهای سراسری‌ای که این کلیدِ صفحه عمداً می‌پوشاند
 *  @param page صفحه‌ای که این کار باز می‌کند (Ctrl+۱ تا ۹)
 */
data class KeyAction(
  val id: String,
  val scope: String,
  val title: String,
  val status: KeyStatus,
  val keys: List<Chord>,
  val web: List<Chord> = emptyList(),
  val overrides: List<String> = emptyList(),
  val page: String? = null,
) {
  /** برچسبِ کوتاهِ نخستین کلید برای دکمه‌ها و راهنما: «Ctrl+P» */
  fun hint(mac: Boolean = false): String = keys.first().label(mac)
}

/** کلیدی که سیستم یا مرورگر نگه می‌دارد و هیچ میان‌بری رویش نمی‌نشیند */
data class ReservedKey(val chord: Chord, val why: String)
