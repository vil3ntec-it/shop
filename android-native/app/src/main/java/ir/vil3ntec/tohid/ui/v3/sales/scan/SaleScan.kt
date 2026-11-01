package ir.vil3ntec.tohid.ui.v3.sales.scan

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartNote
import kotlinx.coroutines.channels.Channel

/**
 *  اسکنِ پیوستهٔ صندوق (`scan?for=sale`): برگهٔ اسکن هر کد را این‌جا می‌گذارد و صندوقِ زیرش
 *  (`SaleRoute`، که زیرِ برگه زنده می‌ماند) می‌خواند، به سبد می‌برد و پاسخ را در `note`
 *  می‌نویسد تا همان برگه نشانش دهد — دوربین پس از هر کالا نمی‌ایستد.
 */
object SaleScan {
  /** یک خواندن؛ `manual` = بارکدِ دستی (سدِ تکرارِ دوربین ندارد) */
  data class Read(val code: String, val manual: Boolean = false)

  val reads = Channel<Read>(Channel.UNLIMITED)

  /** پاسخِ آخرین خواندن (شمارهٔ `seq` تا دو پاسخِ یکسانِ پشتِ‌هم هم دیده شوند) */
  data class Shown(val seq: Long, val note: CartNote)

  val note = mutableStateOf<Shown?>(null)

  /** «هر اسکن ×N» */
  val multiplier = mutableIntStateOf(1)

  /** برگه همین حالا باز است ⇒ پاسخ‌ها در برگه، نه پیامِ پوسته */
  val open = mutableStateOf(false)

  fun post(note: CartNote) {
    this.note.value = Shown(System.nanoTime(), note)
  }

  /** «هر اسکن» — همان چهار ضریبِ صندوقِ امروز */
  val MULTIPLIERS = listOf(1, 2, 5, 10)
}
