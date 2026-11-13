package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.i18n.StrImagePages
import ir.vil3ntec.tohid.core.image.BankPhotoCard
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.ImageBank
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 *  «منبعِ عکس» زیرِ عکسِ کالا (v3 ۳۷.۷؛ IMG-BANK-08، ۰۹، ۱۶) — فقط با بانکِ روشن
 *  (`shop.image_bank`). کدام عکس در ویترینِ مشتری دیده می‌شود، بی هیچ حدسی؛ نسخهٔ تازهٔ
 *  مرکزی فقط پیشنهاد است؛ بازگشت به عکسِ مرکزی و پیشنهادِ عکسِ خود به بانک (فقط صاحبِ
 *  دکان و فقط با تیکِ اجازه). تصمیمِ «چه نشان داده شود» در `BankPhotoCard`ِ core است.
 *
 *  `refresh` با هر عکسِ تازه بالا می‌رود تا کارت دوباره از سرور خوانده شود؛
 *  `onPhoto` بایت‌های عکسی است که پس از کار روی سرور ثبت شد (برای همین دستگاه).
 */
@Composable
fun BankPhotoSource(
  productId: String,
  name: String,
  barcode: String,
  refresh: Int,
  onPhoto: (ByteArray) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  var state by remember(productId) { mutableStateOf<ImageBank.SourceState?>(null) }
  var problem by remember(productId) { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var consent by remember(productId) { mutableStateOf(false) }
  var reload by remember { mutableStateOf(0) }
  val owner = remember { ShopRole.current(context) == ShopRole.OWNER }

  LaunchedEffect(productId, refresh, reload) {
    state = try {
      ImageBank.source(context, productId).also { problem = null }
    } catch (c: CancellationException) {
      throw c
    } catch (f: ApiFailure) {
      problem = if (f is ApiFailure.Offline || f is ApiFailure.Timeout) StrImagePages.sourceOffline else f.userMessage
      null
    } catch (e: Exception) {
      problem = StrImagePages.sourceUnread
      null
    }
  }

  fun perform(action: BankPhotoCard.Action) {
    busy = true; problem = null
    scope.launch {
      try {
        ImageBank.applySource(context, productId, action, name, barcode)?.let(onPhoto)
        consent = false
        reload++
      } catch (c: CancellationException) {
        throw c
      } catch (f: ApiFailure) {
        problem = when (f.code) {
          "needs_confirmation" -> StrImagePages.notCertainUseBank
          "barcode_mismatch" -> StrImagePages.barcodeMismatch
          "image_not_selectable" -> StrImagePages.noLongerSelectable
          "version_not_approved" -> StrImagePages.versionNotApproved
          "consent_required" -> StrImagePages.consentNeeded
          else -> if (f is ApiFailure.Offline || f is ApiFailure.Timeout) StrImagePages.offlineRetry else f.userMessage
        }
      } catch (e: Exception) {
        problem = e.message ?: StrImagePages.notDone
      } finally {
        busy = false
      }
    }
  }

  val s = state
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
      .border(BorderStroke(1.dp, colors.border), RoundedCornerShape(12.dp))
      .background(colors.surface).padding(10.dp),
  ) {
    Text(StrImagePages.photoSource, style = MaterialTheme.typography.labelMedium, color = colors.muted)
    if (s == null) {
      Text(problem ?: StrAccount.reading, style = MaterialTheme.typography.bodySmall, color = if (problem != null) colors.warning else colors.muted)
      if (problem != null) TextButton(onClick = { reload++ }) { Text(Str.again) }
    } else {
      val card = remember(s, owner) { BankPhotoCard.card(s.resolved, s.proposals, owner) }
      Text(headline(card), style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(4.dp))
      card.notes.forEach { n -> val (t, tone) = note(n); StatusPill(t, tone); Spacer(Modifier.height(3.dp)) }
      problem?.let { StatusPill(it, PillTone.DANGER); Spacer(Modifier.height(3.dp)) }
      card.actions.forEach { a ->
        Spacer(Modifier.height(Space.xxs))
        when (a) {
          is BankPhotoCard.Action.Propose -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { consent = !consent }) {
              Checkbox(checked = consent, onCheckedChange = { consent = it })
              Text(
                StrImagePages.consentText,
                style = MaterialTheme.typography.bodySmall, color = colors.text,
              )
            }
            OutlinedButton(onClick = { perform(a) }, enabled = !busy && consent) { Text(StrImagePages.proposeCentral) }
          }
          is BankPhotoCard.Action.RevertToCentral ->
            OutlinedButton(onClick = { perform(a) }, enabled = !busy) { Text(StrImagePages.backToCentral) }
          is BankPhotoCard.Action.UseCentralVersion ->
            OutlinedButton(onClick = { perform(a) }, enabled = !busy) {
              Text(if (a.newer) StrImagePages.newerCentral(a.version) else StrImagePages.previousVersion(a.version))
            }
        }
      }
      //  §۳۷.۷: «گزارشِ عکسِ اشتباه یا بستهٔ قدیمی» — فقط عکسِ مرکزیِ دیده‌شده
      Spacer(Modifier.height(Space.xxs))
      BankPhotoReportBox(ir.vil3ntec.tohid.core.image.BankPhotoReport.reportable(s.resolved)) { id, kind, note -> ImageBank.report(context, id, kind, note) }
    }
  }
}

private fun headline(c: BankPhotoCard.Card): String = when (c.source) {
  BankPhotoCard.Source.CENTRAL -> StrImagePages.centralVersion(c.version)
  BankPhotoCard.Source.SHOP -> StrImageChanges.sourceShop
  BankPhotoCard.Source.LEGACY -> StrImagePages.thisDevicePhoto
  BankPhotoCard.Source.NONE -> StrImageChanges.sourceNone
}

private fun note(n: BankPhotoCard.Note): Pair<String, PillTone> = when (n) {
  BankPhotoCard.Note.SHOWN_IN_STOREFRONT -> StrImagePages.shownInStore to PillTone.INFO
  BankPhotoCard.Note.PLACEHOLDER -> StrImagePages.placeholderNote to PillTone.NEUTRAL
  BankPhotoCard.Note.LEGACY_DEVICE -> StrImagePages.viaSync to PillTone.NEUTRAL
  BankPhotoCard.Note.USER_CONFIRMED -> StrImagePages.userConfirmed to PillTone.WARNING
  BankPhotoCard.Note.ARCHIVED -> StrImagePages.archivedInBank to PillTone.WARNING
  BankPhotoCard.Note.CENTRAL_KEPT -> StrImagePages.centralKept to PillTone.NEUTRAL
  BankPhotoCard.Note.PROPOSAL_PENDING -> StrImagePages.proposalPending to PillTone.REVIEW
  BankPhotoCard.Note.PROPOSAL_APPROVED -> StrImagePages.proposalApproved to PillTone.SUCCESS
}
