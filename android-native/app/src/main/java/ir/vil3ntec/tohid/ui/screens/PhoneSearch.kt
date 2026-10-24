package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.v3.shell.SearchIndex
import ir.vil3ntec.tohid.ui.v3.shell.SearchScreen

/**
 *  جست‌وجوی گوشی — حالا `SearchScreen` ِ نسخهٔ ۳ (`ui/v3/shell`). این نام فقط برای
 *  پیش‌نمایشِ امروزِ کامپیوتر (`PhoneV3HomePreview`) مانده و در فاز ۳ برداشته می‌شود؛
 *  هر نتیجه با مسیرِ خودش از `onOpen` می‌رود (کالا هم).
 */
@Composable
fun PhoneSearchScreen(
  d: ShopData,
  onOpen: (String) -> Unit,
  @Suppress("UNUSED_PARAMETER") onProduct: (String) -> Unit,
  onBack: () -> Unit,
  initialQuery: String = "",
  bottomReserve: Dp = PhoneNav.RESERVE,
) {
  val sections = androidx.compose.runtime.remember { SearchIndex.sections() }
  SearchScreen(d, sections, initialQuery, onOpen = onOpen, onScan = {}, onBack = onBack, bottomReserve = bottomReserve)
}

/** دکمهٔ گردِ سفید (r5) — همان `V3IconButton` ِ کیت؛ ویرایشگرِ نوار هنوز با این نام صدایش می‌زند */
@Composable
internal fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) =
  ir.vil3ntec.tohid.ui.v3.V3IconButton(icon, label, onClick = onClick)
