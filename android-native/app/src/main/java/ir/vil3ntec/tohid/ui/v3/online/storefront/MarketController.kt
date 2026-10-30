package ir.vil3ntec.tohid.ui.v3.online.storefront

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.MarketListing
import ir.vil3ntec.tohid.core.storefront.MarketListingDto
import ir.vil3ntec.tohid.core.storefront.MarketListingRepository
import ir.vil3ntec.tohid.core.storefront.MarketLocationDto
import ir.vil3ntec.tohid.core.storefront.MarketStateDto
import ir.vil3ntec.tohid.data.DeviceLocation

/**
 *  «نمایش در بازارچه» (`GET/PUT /api/shop/market`) — همان کارِ `MarketPane` ِ پیشین بیرون از نمایش:
 *  دروازهٔ بار (`MarketListing.gateOf`)، فرم (کلید، دسته، ناحیه، موقعیتِ درشت)، یافتنِ موقعیت که همین‌جا
 *  گرد می‌شود (`coarseOf`)، و ذخیره که فرم را از پاسخِ سرور از نو پر می‌کند.
 */
@Stable
internal class MarketController(private val repo: MarketListingRepository?) {
  enum class Save { IDLE, SAVING, SAVED, FAILED }

  /** null = در حالِ خواندن */
  var gate by mutableStateOf<MarketListing.Gate?>(null)
    private set
  var state by mutableStateOf<MarketStateDto?>(null)
    private set
  var loadError by mutableStateOf("")
    private set
  var form by mutableStateOf(MarketListing.Form(false, "", ""))
    private set
  var categoryError by mutableStateOf<String?>(null)
    private set
  var save by mutableStateOf(Save.IDLE)
    private set
  var saveError by mutableStateOf("")
    private set
  var locating by mutableStateOf(false)
    private set
  var locNote by mutableStateOf<String?>(null)
    private set

  val listing: MarketListingDto get() = state?.listing ?: MarketListingDto()

  /** موقعیتی که فرم الان نشان می‌دهد (نگه‌داشته، برداشته یا تازه) */
  val shownLocation: MarketLocationDto?
    get() = when (val l = form.location) {
      MarketListing.LocationEdit.Keep -> listing.location
      MarketListing.LocationEdit.Clear -> null
      is MarketListing.LocationEdit.Set -> l.location
    }

  suspend fun reload() {
    val r = repo ?: return
    gate = null
    r.load()
      .onSuccess { show(it) }
      .onFailure { e -> loadError = e.userMessage; gate = MarketListing.gateOf(e.status, e.code) }
  }

  /** حالتِ آماده (پس از بار یا برای پیش‌نمایش) */
  fun show(s: MarketStateDto) {
    state = s
    form = MarketListing.Form(s.listing.listed, s.listing.category, s.listing.area)
    gate = MarketListing.Gate.READY
  }

  fun edit(f: MarketListing.Form) {
    if (f.listed != form.listed || f.category != form.category) categoryError = null
    form = f
    if (save != Save.SAVING) save = Save.IDLE
  }

  /** فقط درشت نگه داشته می‌شود — نقطهٔ دقیقِ دستگاه همین‌جا گرد می‌شود */
  suspend fun locate(context: Context) {
    locating = true
    locNote = null
    val fix = DeviceLocation.current(context)
    locating = false
    val coarse = fix?.let { MarketListing.coarseOf(it.lat, it.lng) }
    if (coarse == null) locNote = StrOnline.mkNoFix
    else edit(form.copy(location = MarketListing.LocationEdit.Set(coarse)))
  }

  fun denied() { locNote = StrOnline.mkNoPermission }

  fun clearLocation() {
    locNote = null
    edit(form.copy(location = MarketListing.LocationEdit.Clear))
  }

  /** @return true اگر ذخیره شد (برای پیامِ کوتاه) */
  suspend fun submit(): Boolean {
    val r = repo ?: return false
    val categories = MarketListing.categoriesOf(listing)
    val errors = MarketListing.errors(form, categories)
    categoryError = errors["category"]
    if (errors.isNotEmpty()) return false
    save = Save.SAVING
    var ok = false
    r.save(form)
      .onSuccess { saved ->
        state = (state ?: MarketStateDto()).copy(listing = saved)
        form = MarketListing.Form(saved.listed, saved.category, saved.area)
        save = Save.SAVED
        ok = true
      }
      .onFailure { e -> saveError = e.userMessage; save = Save.FAILED }
    return ok
  }
}
