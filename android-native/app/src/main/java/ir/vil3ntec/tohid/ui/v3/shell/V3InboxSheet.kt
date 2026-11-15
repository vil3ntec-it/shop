package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrV3Inbox
import ir.vil3ntec.tohid.core.sync.V3Inbox
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.launch

/**
 *  SYN-07 / S28 — یک تعارض: دو سو کنارِ هم («فعلی» = آنچه سرور نگه داشت، «دیگر» = آنچه نبرد) با دستگاه، زمان و مقدارِ هر
 *  فیلد؛ زیرِ هر سو دکمهٔ «همین بماند» با برچسبِ «مالِ من / مالِ دستگاهِ دیگر» (یا «فعلی / دیگر» روی دستگاهِ سوم)؛
 *  و — فقط ردیفِ چندفیلدیِ ادغام‌شدنی و با `inbox.merge` ِ سرور — «ادغام، فیلد به فیلد».
 *  ⛔ هر کار یک تصمیم در صف است ([V3InboxModel.decide]): دکمه تا پایانِ همان تلاش قفل؛ بی‌اینترنت «در صف».
 */
@Composable
internal fun V3InboxSheet(cf: V3Inbox.Conflict, model: V3InboxModel, data: ShopData, onClose: () -> Unit) {
  val scope = rememberCoroutineScope()
  val c = PhoneV3.colors
  var busy by remember { mutableStateOf(false) }
  var merging by remember(cf.id) { mutableStateOf(false) }
  var takeOther by remember(cf.id) { mutableStateOf<Set<String>>(emptySet()) }
  val serverMerge by model.merge
  val queued = model.pending(cf.id)
  val picks = if (queued != null) emptyList() else V3Inbox.picks(cf, serverMerge)
  val (cur, other) = V3Inbox.sides(cf, model.me)
  val (curOwner, otherOwner) = V3Inbox.owners(cur, other)

  fun go(pick: V3Inbox.Pick, take: Set<String> = emptySet()) {
    val d = V3Inbox.decide(cf, pick, serverMerge, take) ?: return
    busy = true
    scope.launch {
      try { model.decide(d) } finally { busy = false }
      onClose()
    }
  }

  //  همیشه تمام‌قد: دو سو باید با هم دیده شوند (نیمه‌باز سوی دوم را زیرِ لبه می‌برد)
  V3Sheet(onDismiss = onClose, title = v3RowTitle(cf, data), detents = false) { _ ->
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(StrV3Inbox.kind(cf.kind), style = V3Type.body, color = c.ink)
      Text(SaleTime.stamp(cf.at), style = V3Type.footnote, color = c.inkMuted)
      if (!cf.task) {
        SidePanel(StrV3Inbox.current, cur, cf)
        if (V3Inbox.Pick.KEEP_CURRENT in picks) V3Button(keepLabel(curOwner, current = true), { go(V3Inbox.Pick.KEEP_CURRENT) }, Modifier.fillMaxWidth(), enabled = !busy, loading = busy)
        SidePanel(StrV3Inbox.other, other, cf)
        if (V3Inbox.Pick.TAKE_OTHER in picks) V3Button(keepLabel(otherOwner, current = false), { go(V3Inbox.Pick.TAKE_OTHER) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, enabled = !busy)
      }
      if (V3Inbox.Pick.MERGE in picks && !merging) {
        V3Button(StrV3Inbox.merge, { merging = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.CallMerge, enabled = !busy)
      }
      if (merging) MergePicker(cf, takeOther, { takeOther = it }, busy) { go(V3Inbox.Pick.MERGE, takeOther) }
      if (V3Inbox.Pick.REVIEWED in picks) V3Button(StrV3Inbox.reviewed, { go(V3Inbox.Pick.REVIEWED) }, Modifier.fillMaxWidth(), enabled = !busy, loading = busy)
      when {
        queued != null -> Text(StrV3Inbox.queued, style = V3Type.footnote, color = c.inkMuted)
        !cf.open -> Text(StrV3Inbox.closedAs(v3Status(cf), cf.resolvedBy.takeLast(8).ifBlank { "—" }), style = V3Type.subhead, color = c.inkMuted)
      }
      model.notes.value[cf.id]?.let { Text(v3NoteText(it), style = V3Type.footnote, color = c.inkMuted) }
      Spacer(Modifier.height(8.dp))
    }
  }
}

private fun keepLabel(o: V3Inbox.Owner, current: Boolean): String = when (o) {
  V3Inbox.Owner.MINE -> StrV3Inbox.keepMine
  V3Inbox.Owner.THEIRS -> StrV3Inbox.keepTheirs
  V3Inbox.Owner.NEUTRAL -> if (current) StrV3Inbox.keepCurrent else StrV3Inbox.takeOther
}

private fun whoText(s: V3Inbox.Side): String = when (s.who) {
  V3Inbox.Who.THIS_DEVICE -> StrV3Inbox.thisDevice
  V3Inbox.Who.OTHER_DEVICE -> StrV3Inbox.otherDevice(s.device.takeLast(6))
  V3Inbox.Who.SERVER -> StrV3Inbox.server
  V3Inbox.Who.UNKNOWN -> StrV3Inbox.unknownDevice
}

/** یک سو: عنوان، دستگاه و زمانِ نوشته، و مقدارِ هر فیلد (یا «حذف شده» / «ردیف می‌ماند») */
@Composable
private fun SidePanel(title: String, s: V3Inbox.Side, cf: V3Inbox.Conflict) {
  val c = PhoneV3.colors
  V3Card {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = V3Type.callout, color = if (s.current) c.ink else c.inkMuted)
      Text(listOfNotNull(whoText(s), s.wall?.let { StrV3Inbox.at(SaleTime.stamp(it)) }).joinToString(" · "), style = V3Type.footnote, color = c.inkMuted)
      V3Divider()
      when {
        s.deleted -> Text(StrV3Inbox.deleted, style = V3Type.body, color = c.ink)
        s.values.isEmpty() && cf.field == V3Inbox.DELETE -> Text(StrV3Inbox.rowKept, style = V3Type.body, color = c.ink)
        else -> s.values.forEach { (f, v) -> FieldRow(StrV3Inbox.field(V3Inbox.base(f)), v3Value(f, v)) }
      }
    }
  }
}

@Composable
private fun FieldRow(label: String, value: String) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = V3Type.subhead, color = c.inkMuted)
    Text(value, style = V3Type.subhead, color = c.ink)
  }
}

/** ادغام: برای هر فیلد «فعلی | دیگر»؛ «ثبتِ تصمیم» ⇒ فیلدهای «دیگر» برمی‌گردند (همه ⇒ برگرداندن، هیچ ⇒ نگه داشتن) */
@Composable
private fun MergePicker(cf: V3Inbox.Conflict, take: Set<String>, onTake: (Set<String>) -> Unit, busy: Boolean, onApply: () -> Unit) {
  val c = PhoneV3.colors
  val (cur, other) = V3Inbox.sides(cf, "")
  V3Card {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(StrV3Inbox.mergeTitle, style = V3Type.headline, color = c.ink)
      Text(StrV3Inbox.mergeBody, style = V3Type.footnote, color = c.inkMuted)
      other.values.keys.forEach { f ->
        Text(StrV3Inbox.field(V3Inbox.base(f)), style = V3Type.subhead, color = c.ink)
        V3Segmented(
          listOf(StrV3Inbox.pickCurrent + " · " + v3Value(f, cur.values[f]), StrV3Inbox.pickOther + " · " + v3Value(f, other.values[f])),
          if (f in take) 1 else 0,
          { i -> onTake(if (i == 1) take + f else take - f) },
        )
      }
      V3Button(StrV3Inbox.apply, onApply, Modifier.fillMaxWidth(), enabled = !busy, loading = busy)
    }
  }
}
