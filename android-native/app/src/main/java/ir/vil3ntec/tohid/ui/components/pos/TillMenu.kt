package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import ir.vil3ntec.tohid.ui.v3.V3Hue

/*
 *  «…» ِ سبدِ گوشی (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «بخش سبد صدها دکمه داره، اون‌ها چیه؟») — نمایشِ تدریجی، هیچ قابلیتی برداشته نمی‌شود.
 *  ابزارهای سبد (پیش‌فاکتور، سفارشِ ویژه، کارتِ هدیه، امانی، لیستِ قیمت، دریافتِ بدهی، یادداشت، فروشندهٔ ردیف‌ها، تحویلِ بعدی)
 *  همان composable های همیشگی‌اند با همان تنظیم، همان پنجره‌ها و همان قاعده‌ها؛ فقط وقتی [LocalTillMenu] هست (صندوقِ گوشی)
 *  به‌جای دکمه‌ها هر کدام **یک ردیف با توضیحِ یک‌خطی** به همین فهرست می‌دهد ([TillOffer]) و «…» آن‌ها را نشان می‌دهد. صندوقِ
 *  کامپیوتر (`DeskSale`) این را نمی‌دهد ⇒ همان دکمه‌های پیشین، بایت‌به‌بایت.
 *
 *  قاعده‌ها: تنظیمِ خاموش ⇒ ابزار پیش از هر چیز `return` می‌کند و ردیفی نمی‌دهد؛ فهرستِ خالی (پیش‌فاکتورِ صفر، سفارشِ ویژهٔ
 *  صفر) ⇒ ردیفِ `null`؛ نشانِ کوچکِ کنارِ جمع ([TillNotice]) فقط وقتی چیزی غیرِ پیش‌فرض است (مثلاً «لیستِ قیمت: عمده»).
 */

/** یک ردیفِ «…» ِ سبد: عنوان، توضیحِ ساده، آیکن و کار. [group] ۰ = همین فاکتور، ۱ = کارهای دیگرِ صندوق */
data class TillAction(
  val order: Int,
  val title: String,
  val hint: String,
  val icon: ImageVector,
  val hue: V3Hue,
  val group: Int = 0,
  val onClick: () -> Unit,
)

/** فهرستِ زندهٔ «…» و نشان‌های کنارِ جمع — هر ابزار با کلیدِ خودش می‌نویسد و با رفتنش پاک می‌شود */
@Stable
class TillMenu {
  private val items = mutableStateMapOf<String, TillAction>()
  private val notes = mutableStateMapOf<String, String>()

  /** ردیف‌ها به ترتیب (گروه، سپس `order`) */
  val actions: List<TillAction> get() = items.values.sortedWith(compareBy({ it.group }, { it.order }))

  /** نشان‌های کم‌رنگِ کنارِ جمع، به ترتیبِ کلید */
  val notices: List<String> get() = notes.entries.sortedBy { it.key }.map { it.value }

  /** کلیدهای ردیف‌های همین لحظه (آزمون و شمارش) */
  val keys: Set<String> get() = items.keys.toSet()

  internal fun put(key: String, action: TillAction?) {
    if (action == null) items.remove(key) else items[key] = action
  }

  internal fun note(key: String, text: String?) {
    if (text == null) notes.remove(key) else if (notes[key] != text) notes[key] = text
  }
}

/** صندوقِ گوشی این را می‌دهد؛ `null` (کامپیوتر، پیش‌نمایشِ قدیم) ⇒ هر ابزار دکمه‌های خودش را می‌کشد */
val LocalTillMenu = staticCompositionLocalOf<TillMenu?> { null }

/** ردیفِ [key] تا وقتی این ابزار روی صفحه است؛ `null` ⇒ ردیفی نیست (مثلاً فهرستِ خالی) */
@Composable
fun TillOffer(key: String, action: TillAction?) {
  val menu = LocalTillMenu.current ?: return
  SideEffect { menu.put(key, action) }
  DisposableEffect(menu, key) { onDispose { menu.put(key, null) } }
}

/** نشانِ کم‌رنگِ کنارِ جمعِ سبد؛ `null` ⇒ هیچ (حالتِ پیش‌فرض) */
@Composable
fun TillNotice(key: String, text: String?) {
  val menu = LocalTillMenu.current ?: return
  SideEffect { menu.note(key, text) }
  DisposableEffect(menu, key) { onDispose { menu.note(key, null) } }
}
