package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.platform.DocChainSync

/**
 *  SEC-02 — مرزِ سکوی «بررسیِ یکپارچگیِ اسناد»: پرچمِ ذخیره‌شدهٔ `shop.doc_chain` (`DocChainSync.enabled`؛ نپرسیده ⇐ خاموش ⇒
 *  نه در، نه صفحه) و سنجشِ دیسک بیرون از رشتهٔ اصلی (`DocChainSync.audit`). [DocChainCheckRun.again] سنجش را از نو می‌زند.
 */
class DocChainCheckRun(val state: DocChainCheckState, val again: () -> Unit)

@Composable
fun rememberDocChainOn(): Boolean {
  val context = LocalContext.current
  return remember { DocChainSync.enabled(context) }
}

@Composable
fun rememberDocChainCheck(d: ShopData): DocChainCheckRun {
  val context = LocalContext.current
  var round by remember { mutableIntStateOf(0) }
  val state by produceState<DocChainCheckState>(DocChainCheckState.Loading, round) {
    value = DocChainCheckState.Loading
    value = DocChainSync.audit(context, d)?.let { DocChainCheckState.Done(it) } ?: DocChainCheckState.Off
  }
  return DocChainCheckRun(state) { round++ }
}
