package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.MarketListing
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow

/**
 *  برگهٔ «بازارچه»: کلیدِ «در بازارچه نشان بده» با هشدارِ «هنوز دیده نمی‌شود»، دستهٔ دکان (برگهٔ
 *  انتخاب)، ناحیه با شمارِ نویسه، و موقعیتِ تقریبی (همین‌جا / برداشتن؛ کامپیوتر مکان‌یاب ندارد).
 *  «ذخیرهٔ بازارچه» نوارِ چسبانِ پایین است. بی اجازه یا بی اشتراک ⇐ فقط دیدن. دروازه‌ها همان متن‌های امروز.
 */
@Composable
internal fun MarketPane(
  ctl: MarketController,
  config: StorefrontConfigDto,
  hasLocator: Boolean,
  onLocate: () -> Unit,
  onRetry: () -> Unit,
  pad: PaddingValues,
) {
  val s = ctl.state
  when {
    ctl.gate == null -> Column(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 3) }
    ctl.gate == MarketListing.Gate.ERROR -> V3EmptyState(
      Icons.Outlined.CloudOff, StrOnline.mkLoadFailed, body = ctl.loadError.ifBlank { StrOnline.checkConnection }, action = StrKit.retry, onAction = onRetry,
    )
    ctl.gate == MarketListing.Gate.MARKET_OFF || (ctl.gate == MarketListing.Gate.READY && s != null && !s.directory) ->
      V3EmptyState(Icons.Outlined.Storefront, StrOnline.mkOffTitle, body = StrOnline.mkOffBody)
    ctl.gate == MarketListing.Gate.STOREFRONT_OFF -> V3EmptyState(Icons.Outlined.Storefront, StrOnline.sfOffTitle, body = StrOnline.mkStorefrontOffBody)
    ctl.gate == MarketListing.Gate.NO_PERMISSION -> V3EmptyState(Icons.Outlined.Lock, StrOnline.sfOwnerTitle, body = StrOnline.mkOwnerBody)
    !config.permissions.manage || !config.entitled -> MarketReadOnly(ctl, config, pad)
    else -> MarketForm(ctl, config, hasLocator, onLocate, pad)
  }
}

/** آیا نوارِ «ذخیرهٔ بازارچه» دیده شود؟ (فقط فرمِ قابلِ ویرایش) */
internal fun marketEditable(ctl: MarketController, config: StorefrontConfigDto): Boolean =
  ctl.gate == MarketListing.Gate.READY && ctl.state?.directory == true && config.permissions.manage && config.entitled

@Composable
private fun MarketForm(ctl: MarketController, config: StorefrontConfigDto, hasLocator: Boolean, onLocate: () -> Unit, pad: PaddingValues) {
  val f = ctl.form
  val cats = MarketListing.categoriesOf(ctl.listing)
  var picking by remember { mutableStateOf(false) }
  val settings = config.settings
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
  ) {
    V3Section(StrOnline.mkTitle, footer = StrOnline.mkBody) {
      V3ToggleRow(StrOnline.mkToggle, f.listed, { ctl.edit(f.copy(listed = it)) }, icon = Icons.Outlined.Visibility, hue = V3Hue.BRAND)
      V3Divider(start = 64.dp)
      V3PickerRow(
        StrOnline.mkCategory, MarketListing.labelOf(f.category, cats), { picking = true },
        placeholder = StrOnline.mkPickCategory, icon = Icons.Outlined.Category, error = ctl.categoryError,
      )
      V3Divider()
      V3TextField(
        f.area, { ctl.edit(f.copy(area = it)) }, StrOnline.mkArea, placeholder = StrOnline.mkAreaPlaceholder,
        helper = StrOnline.mkAreaHelper("${MarketListing.cleanArea(f.area).length}/${MarketListing.AREA_MAX}"),
        error = MarketListing.areaError(f.area), modifier = Modifier.fillMaxWidth().padding(12.dp),
      )
    }
    if (f.listed && (settings == null || !settings.enabled)) {
      V3Message(V3Tone.WARNING, StrOnline.mkHidden(settings != null), Modifier.padding(top = 12.dp))
    }
    V3Section(StrOnline.mkLocation, footer = StrOnline.mkLocationBody) {
      val loc = ctl.shownLocation
      V3ListRow(StrOnline.locationText(loc?.lat, loc?.lng), icon = Icons.Outlined.Place, hue = V3Hue.MINT, chevron = false)
      if (!hasLocator) { V3Divider(start = 64.dp); V3ListRow(StrOnline.mkDesktopNote, subtitleLines = 3, tile = false, chevron = false) }
      V3Divider()
      //  دو دکمه زیرِ هم تا برچسبِ بلندشان بریده نشود
      Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasLocator) V3Button(
          if (ctl.locating) StrOnline.mkLocating else StrOnline.mkSetHere, onLocate, Modifier.fillMaxWidth(),
          kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.MyLocation, loading = ctl.locating,
          enabled = ctl.save != MarketController.Save.SAVING,
        )
        if (loc != null) V3Button(
          StrOnline.mkRemoveLocation, { ctl.clearLocation() }, Modifier.fillMaxWidth(),
          kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.WrongLocation, enabled = ctl.save != MarketController.Save.SAVING,
        )
      }
    }
    ctl.locNote?.let { V3Message(V3Tone.WARNING, it, Modifier.padding(top = 12.dp)) }
    when (ctl.save) {
      MarketController.Save.SAVED -> V3Message(V3Tone.SUCCESS, if (ctl.listing.listed) StrOnline.mkSavedListed else StrOnline.mkSavedHidden, Modifier.padding(top = 12.dp))
      MarketController.Save.FAILED -> V3Message(V3Tone.ERROR, StrOnline.mkSaveFailed(ctl.saveError), Modifier.padding(top = 12.dp))
      else -> Unit
    }
    Spacer(Modifier.height(24.dp))
  }
  if (picking) {
    V3PickerSheet(
      title = StrOnline.mkCategory,
      options = cats.map { it.label },
      selected = MarketListing.labelOf(f.category, cats),
      onPick = { label -> cats.firstOrNull { it.label == label }?.let { ctl.edit(f.copy(category = it.key)) } },
      onDismiss = { picking = false },
      allowNew = false,
    )
  }
}

/** کاربرِ بی اجازهٔ تنظیم یا دکانِ بی اشتراکِ ویترین: فقط دیدن، بی کلید و دکمه */
@Composable
private fun MarketReadOnly(ctl: MarketController, config: StorefrontConfigDto, pad: PaddingValues) {
  val l = ctl.listing
  val cats = MarketListing.categoriesOf(l)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
    V3Section(StrOnline.mkTitle) {
      V3ListRow(if (l.listed) StrOnline.mkListed else StrOnline.mkNotListed, icon = Icons.Outlined.Visibility, hue = V3Hue.BRAND, chevron = false)
      V3Divider(start = 64.dp)
      V3ListRow(StrOnline.mkCategoryLabel, value = MarketListing.labelOf(l.category, cats).ifBlank { "—" }, icon = Icons.Outlined.Category, hue = V3Hue.VIOLET, chevron = false)
      V3Divider(start = 64.dp)
      V3ListRow(StrOnline.mkAreaLabel, value = l.area.ifBlank { "—" }, icon = Icons.Outlined.Place, hue = V3Hue.AMBER, chevron = false)
      V3Divider(start = 64.dp)
      V3ListRow(StrOnline.locationText(l.location?.lat, l.location?.lng), icon = Icons.Outlined.MyLocation, hue = V3Hue.MINT, chevron = false)
    }
    V3Message(V3Tone.WARNING, if (!config.permissions.manage) StrOnline.mkReadOnlyOwner else StrOnline.mkReadOnlyPlan, Modifier.padding(top = 12.dp))
  }
}
