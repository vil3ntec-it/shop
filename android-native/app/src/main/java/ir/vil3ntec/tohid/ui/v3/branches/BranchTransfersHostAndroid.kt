package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import ir.vil3ntec.tohid.data.BranchDesk
import ir.vil3ntec.tohid.data.BranchTransferBook
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.BranchTransfersRepository
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/*
 *  مرزِ سکوی صفحه‌های «انتقال میانِ شعبه‌ها» (`BranchTransfersPages.kt`، `DeskBranchTransfers.kt`): سرور (`Backend`)، شعبهٔ میز
 *  (`BranchDesk`) و آینهٔ دفترِ دستگاه (`BranchTransferBook`). باقیِ صفحه بی `Context` است (راهِ آیفون).
 */

/** شعبهٔ میز و پیوندِ آینهٔ دفتر برای حال‌ها */
@Composable
internal fun RouteScope.rememberTransferHost(): Pair<BranchTransfersRepository?, Pair<String, suspend (List<BranchTransfer.Transfer>) -> Unit>> {
  val context = LocalContext.current
  //  همان راهِ `PurchaseOrderHost`: بی ورود هر درخواست روشن رد می‌شود (صفحه «نیامد» می‌گوید)، نه صفحهٔ خالی
  val here = remember { BranchDesk.current(context) }
  val repo: BranchTransfersRepository? = remember(context) { BranchTransfersRepository(Backend.api(context)) }
  val st = store
  val mirror: suspend (List<BranchTransfer.Transfer>) -> Unit = { ts -> BranchTransferBook.mirror(context, st, here, ts) }
  return repo to (here to mirror)
}

