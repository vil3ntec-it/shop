package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.shopper.AccountPulls
import ir.vil3ntec.tohid.core.shopper.Cart
import ir.vil3ntec.tohid.core.shopper.CartRules
import ir.vil3ntec.tohid.core.shopper.CheckoutFlow
import ir.vil3ntec.tohid.core.shopper.CheckoutForm
import ir.vil3ntec.tohid.core.shopper.CheckoutKeys
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem
import ir.vil3ntec.tohid.core.shopper.CheckoutRules
import ir.vil3ntec.tohid.core.shopper.CodeSent
import ir.vil3ntec.tohid.core.shopper.MarketOverview
import ir.vil3ntec.tohid.core.shopper.ProductPage
import ir.vil3ntec.tohid.core.shopper.RecentShop
import ir.vil3ntec.tohid.core.shopper.Refills
import ir.vil3ntec.tohid.core.shopper.SavedOrder
import ir.vil3ntec.tohid.core.shopper.ShopAddress
import ir.vil3ntec.tohid.core.shopper.ShopList
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperMemory
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperState
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.core.shopper.SlotsPage
import ir.vil3ntec.tohid.core.shopper.StorePage
import ir.vil3ntec.tohid.core.shopper.TrackRules
import ir.vil3ntec.tohid.core.shopper.AddressBook
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.OrderExtras
import ir.vil3ntec.tohid.core.shopper.PinText
import ir.vil3ntec.tohid.core.shopper.SavedAddress
import ir.vil3ntec.tohid.core.text.LocaleFormat
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** صفحه‌های برنامهٔ خرید (پشتهٔ ساده؛ «برگشت» یکی برمی‌دارد). */
sealed class Route {
  object Home : Route()
  data class Shop(val slug: String) : Route()
  data class Product(val slug: String, val id: String) : Route()
  data class CartPage(val slug: String) : Route()
  data class Checkout(val slug: String) : Route()
  data class Placed(val slug: String, val token: String) : Route()
  data class Track(val slug: String, val token: String) : Route()
  object Settings : Route()
  /** مرحلهٔ ۲: نشانی‌های من (CHK-09)، برگهٔ نشانی، و گفت‌وگو با دکان (CHAT؛ [orderNo] null ⇒ گفت‌وگوی عمومی) */
  object Addresses : Route()
  data class AddressEdit(val id: String?, val fromCheckout: Boolean = false) : Route()
  data class Chat(val slug: String, val orderNo: Long?) : Route()
}

/** حالِ یک بارگیری — صفحه هر چهار را نشان می‌دهد (هیچ «در حالِ بار» ِ بی‌پایان یا خطای بی‌متن). */
sealed class Load<out T> {
  object Idle : Load<Nothing>()
  object Busy : Load<Nothing>()
  data class Ready<out T>(val value: T) : Load<T>()
  data class Failed(val error: ShopperError) : Load<Nothing>()

  fun valueOrNull(): T? = (this as? Ready)?.value
}

private fun <T> ShopperResult<T>.load(): Load<T> = when (this) {
  is ShopperResult.Ok -> Load.Ready(value)
  is ShopperResult.Fail -> Load.Failed(error)
}

/**
 *  حالِ برنامهٔ خرید و همهٔ کارهایش — Compose-state ِ خالص (بی android.*)، پس روی کامپیوتر/آیفون هم کامپایل
 *  می‌شود و آزمونِ JVM دارد (`CustomerModelTest`). هر قاعدهٔ پول، سبد و ثبت در core است (`core/.../shopper`)؛
 *  این‌جا فقط ترتیبِ کارها، حالِ صفحه و نگه‌داری.
 *
 *  [io] نخِ درخواست‌ها (اندروید: `Dispatchers.IO`)؛ [debounceMs] مکثِ جست‌وجو (آزمون: ۰)؛ [locator] موقعیتِ گوشی؛
 *  [secureRandom] مولّدِ امنِ سکو (شناسهٔ دستگاهِ MKT-02)؛ [chatWaitMs] long-poll ِ گفت‌وگو (آزمون: ۰).
 */
class CustomerModel(
  internal val api: ShopperApi,
  private val kv: ShopperKeyValue,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher,
  internal val clock: () -> Long,
  private val debounceMs: Long = 350,
  private val locator: Locator = Locator { it(null) },
  secureRandom: (Int) -> ByteArray = { Random.nextBytes(it) },
  chatWaitMs: Int = 20_000,
  secrets: ShopperKeyValue? = null,
) {
  var saved: ShopperState by mutableStateOf(ShopperMemory.withDevice(ShopperMemory.load(kv)) { secureRandom(16) })
    private set
  var stack: List<Route> by mutableStateOf(listOf(Route.Home))
    internal set
  val route: Route get() = stack.last()
  val canGoBack: Boolean get() = stack.size > 1
  var lang: ShopperLang by mutableStateOf(ShopperLang.of(saved.lang))
    private set

  // ── خانه ──
  var market: Load<MarketOverview> by mutableStateOf(Load.Idle)
    private set
  var shopList: Load<ShopList> by mutableStateOf(Load.Idle)
    private set
  var shopQuery: String by mutableStateOf("")
    private set
  var addressError: Boolean by mutableStateOf(false)
    private set

  // ── دکان و کاتالوگ ──
  var store: Load<StorePage> by mutableStateOf(Load.Idle)
    private set
  var storeSlug: String by mutableStateOf("")
    private set
  var query: String by mutableStateOf("")
    private set
  var category: String by mutableStateOf("")
    private set
  var items: List<PublicProduct> by mutableStateOf(emptyList())
    private set
  var cursor: String? by mutableStateOf(null)
    private set
  var catalog: Load<Unit> by mutableStateOf(Load.Idle)
    private set
  var product: Load<ProductPage> by mutableStateOf(Load.Idle)
    private set
  /** پیامِ کوتاهِ گذرا (سقفِ هر سفارش، قیمتِ تازهٔ سبد…) */
  var notice: String? by mutableStateOf(null)
  var cartNote: String? by mutableStateOf(null)
    private set

  // ── ثبت ──
  var form: CheckoutForm by mutableStateOf(CheckoutForm())
    private set
  var code: String by mutableStateOf("")
  var codeSent: CodeSent? by mutableStateOf(null)
    private set
  var verifyBusy: Boolean by mutableStateOf(false)
    private set
  var verifyNote: String? by mutableStateOf(null)
    private set
  var slots: Map<String, Load<SlotsPage>> by mutableStateOf(emptyMap())
    private set
  var placing: Boolean by mutableStateOf(false)
    private set
  var placeNote: String? by mutableStateOf(null)
    private set
  /** پس از ۴۰۹ ِ قیمت: دکمه «پذیرفتن و ثبت» است */
  var repriced: Boolean by mutableStateOf(false)
    private set

  // ── سفارش ──
  var order: Load<PublicOrder> by mutableStateOf(Load.Idle)
    private set
  var orderBusy: Boolean by mutableStateOf(false)
    private set
  var orderNote: String? by mutableStateOf(null)
    private set

  /** کلیدهای پرچم‌دارِ پیگیری (`review`، `chat`) */
  var orderExtras: OrderExtras by mutableStateOf(OrderExtras())
    private set
  /** CHK-10: پین بیرونِ محدودهٔ ارسال است (فقط پیام؛ ثبتِ ارسال را سرور هم می‌بندد) */
  var pinOutside: Boolean by mutableStateOf(false)
    private set
  var pinBusy: Boolean by mutableStateOf(false)
    private set
  var pinNote: String? by mutableStateOf(null)
    private set
  var linkNote: String? by mutableStateOf(null)
    internal set

  // ── مرحلهٔ ۲: بخش‌های جدا (هر کدام فایلِ خودش) ──
  val promo = PromoModel(api, scope, io)
  val feedback = FeedbackModel(api, scope, io)
  /** CHK-24: کارتِ «دستورِ تحویل» ِ پیگیری (`ModelPrefs.kt`) */
  val deliveryPrefs = PrefsModel(api, scope, io) { refreshOrder() }
  val book = AddressModel({ saved.addresses }, { persist(saved.copy(addresses = it)) }, locator, clock)
  val chat = ChatModel(api, kv, scope, io, clock, { saved.ticket?.takeIf { it.expiresAt > clock() } }, { persist(saved.copy(ticket = null)) }, chatWaitMs)
  /** WEB-11/CHK-01: حسابِ مشتری — سبد، ♥ و «فهرستِ خرید» همراهِ حساب (`ModelAccount.kt`؛ [secrets] نگه‌دارِ رمزشدهٔ نشست) */
  val account = AccountBox(secrets)
  /** MKT-11: کدِ کمپینِ پیوند/QR که مشتری با آن به هر دکان آمد (فقط همین اجرا، مثلِ «همین نشست» ِ وب) */
  internal val campaigns = HashMap<String, String>()

  private var shopsJob: Job? = null
  private var searchJob: Job? = null
  private var pollJob: Job? = null

  init {
    ShopperText.lang = lang
    ShopperMemory.save(kv, saved)
    enter(Route.Home)
    chat.drain()
  }

  fun now(): Long = clock()

  internal fun <T> request(call: () -> ShopperResult<T>, done: (ShopperResult<T>) -> Unit): Job =
    scope.launch { done(withContext(io) { call() }) }

  /** کاری پس از [ms] روی همان نخِ صفحه (CHK-03: سکوتِ پس از تغییرِ سبد و درنگِ پس از شکستِ هم‌گامی) */
  internal fun later(ms: Long, block: () -> Unit): Job = scope.launch { delay(ms); block() }

  internal fun persist(s: ShopperState) {
    saved = s
    ShopperMemory.save(kv, s)
  }

  // ── پیمایش ──

  fun go(r: Route) {
    stack = stack + r
    enter(r)
  }

  fun back(): Boolean {
    if (stack.size <= 1) return false
    stack = stack.dropLast(1)
    enter(route, returning = true)
    return true
  }

  fun home() {
    stack = listOf(Route.Home)
    enter(Route.Home, returning = true)
  }

  internal fun enter(r: Route, returning: Boolean = false) {
    if (r !is Route.Track) pollJob?.cancel()
    if (r !is Route.Chat) chat.leave()
    notice = null
    when (r) {
      Route.Home -> { if (market !is Load.Ready && market !is Load.Busy) loadHome(); refreshRefills() }   //  MKT-06: چرخه‌ها از سرور
      is Route.Shop -> ensureStore(r.slug)
      is Route.Product -> {
        ensureStore(r.slug)
        if (!returning || product.valueOrNull()?.product?.id != r.id) loadProduct(r.slug, r.id)
      }
      is Route.CartPage -> {
        ensureStore(r.slug)
        refreshCart(r.slug)
      }
      is Route.Checkout -> prepareCheckout(r.slug)
      is Route.Placed -> Unit
      is Route.Track -> loadOrder(r.slug, r.token, quiet = returning && order.valueOrNull()?.token == r.token)
      Route.Settings, Route.Addresses -> Unit
      is Route.AddressEdit -> if (!returning) {
        if (r.fromCheckout) book.start(null, Triple(form.addressText, form.landmark, form.pin))
        else book.start(saved.addresses.firstOrNull { it.id == r.id })
      }
      is Route.Chat -> chat.open(r.slug, r.orderNo, saved.name.ifBlank { form.name })
    }
  }

  /** برنامه دوباره پیش آمد: پیگیری و سبد از سرور تازه می‌شوند؛ گفت‌وگوی باز ⇒ «خواندم» (CHAT-04) */
  fun resumed() {
    syncAccount(storeSlug, AccountPulls.ALL)
    chat.visible(true)
    chat.online()
    when (val r = route) {
      is Route.Track -> loadOrder(r.slug, r.token, quiet = true)
      is Route.CartPage -> refreshCart(r.slug)
      is Route.Checkout -> refreshCart(r.slug)
      else -> Unit
    }
  }

  /** برنامه پس رفت (`Activity.onPause`، آیفون: `scenePhase` ِ نه active): گفت‌وگوی باز تا برگشتن فقط «رسید» می‌دهد */
  fun paused() = chat.visible(false)

  // ── خانه و بازارچه ──

  private fun loadHome() {
    market = Load.Busy
    request({ api.market() }) { r ->
      market = r.load()
      if (r is ShopperResult.Ok) searchShops(shopQuery)
    }
  }

  fun retryHome() = loadHome()

  fun searchShops(q: String) {
    shopQuery = q
    shopsJob?.cancel()
    if (market !is Load.Ready) return
    shopsJob = scope.launch {
      if (debounceMs > 0 && q.isNotBlank()) delay(debounceMs)
      shopList = Load.Busy
      shopList = withContext(io) { api.shops(q = q) }.load()
    }
  }

  /** نشانی یا پیوندی که مشتری نوشت/چسباند ⇒ دکان؛ نادرست ⇒ پیامِ روشن */
  fun openAddress(text: String): Boolean {
    val slug = ShopAddress.slugOf(text)
    addressError = slug == null
    if (slug != null) go(Route.Shop(slug))
    return slug != null
  }

  // ── دکان و کاتالوگ ──

  private fun ensureStore(slug: String) {
    if (slug == storeSlug && (store is Load.Ready || store is Load.Busy)) return
    loadStore(slug)
  }

  fun loadStore(slug: String) {
    storeSlug = slug
    store = Load.Busy
    items = emptyList()
    cursor = null
    query = ""
    category = ""
    request({ api.store(slug) }) { r ->
      if (slug != storeSlug) return@request
      val moved = r.errorOrNull()?.movedTo()
      if (moved != null && moved != slug) {
        //  S3: نشانیِ تازهٔ دکان — سبد و سفارش‌ها همراهش؛ همان صفحه با نشانیِ تازه
        persist(ShopperMemory.moved(saved, slug, moved))
        stack = stack.map { it.withSlug(slug, moved) }
        loadStore(moved)
        return@request
      }
      store = r.load()
      r.valueOrNull()?.let { page ->
        persist(ShopperMemory.withRecent(saved, RecentShop(slug, page.shop.title, clock())))
        loadCatalog(reset = true)
        enteredStore(slug, page)
        loadRecent(slug, page)   //  WEB-13: «دیده‌شده‌های اخیر» با قیمتِ امروز (فقط با `recent`)
        if (page.features.reviews) feedback.loadShop(slug)
        //  ثبت پیش از رسیدنِ دکان باز شده بود (خانه ⇐ سبد ⇐ ثبت): روشِ پیش‌فرض و بازه‌ها همین حالا
        if (route is Route.Checkout) {
          form = form.copy(fulfillment = CheckoutRules.fulfillmentOf(page.shop, form).orEmpty())
          loadSlots()
        }
      }
    }
  }

  fun search(q: String) {
    query = q
    searchJob?.cancel()
    searchJob = scope.launch {
      if (debounceMs > 0) delay(debounceMs)
      loadCatalog(reset = true)
    }
  }

  fun pickCategory(c: String) {
    category = if (category == c) "" else c
    loadCatalog(reset = true)
  }

  fun loadMore() {
    if (cursor != null && catalog !is Load.Busy) loadCatalog(reset = false)
  }

  fun retryCatalog() = loadCatalog(reset = items.isEmpty())

  private fun loadCatalog(reset: Boolean) {
    val slug = storeSlug
    val q = query
    val cat = category
    val from = if (reset) null else cursor
    catalog = Load.Busy
    request({ api.products(slug, q = q, category = cat, cursor = from) }) { r ->
      if (slug != storeSlug || q != query || cat != category) return@request
      when (r) {
        is ShopperResult.Ok -> {
          items = if (reset) r.value.items else items + r.value.items.filter { n -> items.none { it.id == n.id } }
          cursor = r.value.nextCursor
          catalog = Load.Ready(Unit)
        }
        is ShopperResult.Fail -> catalog = Load.Failed(r.error)
      }
    }
  }

  fun retryProduct() {
    (route as? Route.Product)?.let { loadProduct(it.slug, it.id) }
  }

  private fun loadProduct(slug: String, id: String) {
    product = Load.Busy
    request({ api.product(slug, id) }) { r ->
      product = r.load()
      r.valueOrNull()?.let { sawProduct(slug, it.product) }
    }
  }

  fun photoPath(slug: String, p: PublicProduct, thumb: Boolean): String? =
    if (p.photo) api.photoPath(slug, p.id, p.photoVersion, thumb) else null

  // ── سبد ──

  fun cartOf(slug: String): Cart = ShopperMemory.cartOf(saved, slug)

  private fun setCart(c: Cart) {
    persist(ShopperMemory.withCart(saved, c))
    syncAccount(c.slug)
  }

  fun add(slug: String, p: PublicProduct) {
    val before = cartOf(slug)
    val after = CartRules.add(before, p, clock())
    if (after === before) {
      notice = if (!CartRules.canOrder(p)) ShopperText.stock(p.stock?.status, null)
        else if (CartRules.atMax(CartRules.qtyOf(before, p.id), p.maxQtyMilli)) ShopperText.maxQty(ShopperText.qty(p.maxQtyMilli ?: CartRules.MAX_QTY_MILLI))
        else null
      return
    }
    setCart(after)
  }

  fun step(slug: String, productId: String, up: Boolean) {
    val c = cartOf(slug)
    val line = c.lines.firstOrNull { it.productId == productId } ?: return
    if (up && CartRules.atMax(line.qtyMilli, line.maxQtyMilli)) {
      notice = ShopperText.maxQty(ShopperText.qty(line.maxQtyMilli ?: CartRules.MAX_QTY_MILLI))
      return
    }
    setCart(CartRules.step(c, productId, up, clock()))
  }

  fun remove(slug: String, productId: String) = setCart(CartRules.remove(cartOf(slug), productId, clock()))

  /** قیمت و «هنوز در ویترین» ِ سبد از کاتالوگِ امروز (WEB-12) — هر بار که سبد یا ثبت باز می‌شود */
  fun refreshCart(slug: String) {
    val ids = cartOf(slug).lines.map { it.productId }
    if (ids.isEmpty()) return
    request({ api.productsByIds(slug, ids) }) { r ->
      val fresh = r.valueOrNull() ?: return@request
      val out = CartRules.refresh(cartOf(slug), fresh.items, clock())
      if (out.cart != cartOf(slug)) setCart(out.cart)
      if (route is Route.Checkout) requotePromo()
      cartNote = listOfNotNull(
        ShopperText.repricedCount(out.repriced).takeIf { out.repriced > 0 },
        ShopperText.goneCount(out.gone).takeIf { out.gone > 0 },
      ).joinToString(" ").ifEmpty { null }
    }
  }

  // ── ثبت ──

  private fun prepareCheckout(slug: String) {
    ensureStore(slug)
    placeNote = null
    repriced = false
    if (form.name.isBlank() && form.phone.isBlank()) {
      form = form.copy(
        name = saved.name, addressText = saved.addressText, landmark = saved.landmark,
        phone = saved.ticket?.phone?.let { localPhone(it) }.orEmpty(),
      )
    }
    store.valueOrNull()?.let { page -> form = form.copy(fulfillment = CheckoutRules.fulfillmentOf(page.shop, form).orEmpty()) }
    loadSlots()
    refreshCart(slug)
    promo.load(slug) { requotePromo() }
  }

  /** MKT-01/09: پیش‌نمایشِ تخفیفِ همین سبد از سرور (خاموش ⇒ هیچ) */
  fun requotePromo() = promo.requote(cartOf(storeSlug), saved.ticket, saved.deviceId)

  fun applyPromo() = promo.apply(cartOf(storeSlug), saved.ticket, saved.deviceId)

  fun clearPromo() = promo.clear(cartOf(storeSlug), saved.ticket, saved.deviceId)

  // ── پین و نشانی‌های من در برگهٔ ثبت (CHK-08/09/10) ──

  fun takePin() {
    pinBusy = true
    pinNote = null
    locator.locate { p ->
      pinBusy = false
      if (p == null) pinNote = ShopperText.pinNone else setPin(p)
    }
  }

  fun pastePin(text: String): Boolean {
    val p = PinText.parse(text) ?: run { pinNote = ShopperText.pinBad; return false }
    setPin(p)
    return true
  }

  fun clearPin() {
    form = form.copy(pinLat = null, pinLng = null, pinAccuracyM = null, addressId = null)
    pinOutside = false
    pinNote = null
  }

  /** گزینشِ دستیِ ناحیه پین را برمی‌دارد (همان وب: پینِ ناجور سفارش را رد می‌کرد) */
  fun pickZone(id: String) {
    form = form.copy(zoneId = id, pinLat = null, pinLng = null, pinAccuracyM = null)
    pinOutside = false
  }

  fun useAddress(a: SavedAddress) {
    form = AddressBook.fill(form, a)
    pinOutside = false
    pinNote = null
    locatePin()
  }

  private fun setPin(p: GeoPin) {
    form = form.copy(pinLat = p.lat, pinLng = p.lng, pinAccuracyM = p.accuracyM, addressId = null)
    locatePin()
  }

  /** «کدام ناحیه؟» از سرور برای همین پین، بی ذخیره (`GET /:slug/delivery`)؛ ناحیهٔ پین جای گزینشِ دستی را می‌گیرد */
  private fun locatePin() {
    val pin = form.pin ?: return
    val page = store.valueOrNull() ?: return
    pinOutside = false
    if (CheckoutRules.zones(page) == null) return
    pinBusy = true
    request({ api.locate(storeSlug, pin.lat, pin.lng) }) { r ->
      pinBusy = false
      if (form.pin != pin) return@request
      val v = r.valueOrNull()
      when {
        v == null -> pinNote = r.errorOrNull()?.let { ShopperText.error(it) }
        v.zoned && !v.inZone -> { pinOutside = true; form = form.copy(zoneId = null) }
        else -> form = form.copy(zoneId = v.zone?.id)
      }
    }
  }

  /** برگهٔ «نشانیِ تازه/ویرایش» ذخیره شد ⇒ برگشت (از برگهٔ ثبت ⇒ همان نشانیِ برگزیده) */
  fun saveAddress() {
    val from = route as? Route.AddressEdit ?: return
    val a = book.save() ?: return
    if (from.fromCheckout) form = form.copy(addressId = a.id)
    notice = ShopperText.addressSaved(a.label)
    back()
  }

  fun deleteAddress(id: String) {
    book.remove(id)
    if (form.addressId == id) form = form.copy(addressId = null)
  }

  fun updateForm(f: CheckoutForm) {
    val changedFulfillment = f.fulfillment != form.fulfillment
    form = f
    if (changedFulfillment) loadSlots()
  }

  /** بازه‌های همان روش، فقط اگر دکان زمان‌بندی دارد (CHK-12) */
  private fun loadSlots() {
    val page = store.valueOrNull() ?: return
    val f = form.fulfillment
    if (!CheckoutRules.scheduled(page, f) || slots[f] is Load.Ready || slots[f] is Load.Busy) return
    val slug = storeSlug
    slots = slots + (f to Load.Busy)
    request({ api.slots(slug, f) }) { r -> slots = slots + (f to r.load()) }
  }

  fun slotsPage(): SlotsPage? = slots[form.fulfillment]?.valueOrNull()

  fun problems(): List<CheckoutProblem> {
    val page = store.valueOrNull() ?: return listOf(CheckoutProblem.CLOSED)
    return CheckoutRules.problems(page, cartOf(storeSlug), form, saved.ticket, slotsPage(), clock())
  }

  fun sendCode() {
    val phone = CheckoutRules.phoneOf(form) ?: run {
      verifyNote = ShopperText.problem(CheckoutProblem.PHONE)
      return
    }
    verifyBusy = true
    request({ api.verifyStart(storeSlug, phone) }) { r ->
      verifyBusy = false
      codeSent = r.valueOrNull()
      verifyNote = r.errorOrNull()?.let { ShopperText.error(it) } ?: ShopperText.codeSent
    }
  }

  fun checkCode() {
    val phone = CheckoutRules.phoneOf(form) ?: return
    val digits = LocaleFormat.asciiDigits(code).filter { it in '0'..'9' }
    verifyBusy = true
    request({ api.verifyCheck(storeSlug, phone, digits) }) { r ->
      verifyBusy = false
      when (r) {
        is ShopperResult.Ok -> {
          persist(saved.copy(ticket = r.value))
          codeSent = null
          code = ""
          verifyNote = ShopperText.verified
          chat.reverified()
          if (route is Route.Checkout) requotePromo()
        }
        is ShopperResult.Fail -> verifyNote = ShopperText.error(r.error)
      }
    }
  }

  fun otherNumber() {
    persist(saved.copy(ticket = null))
    codeSent = null
    code = ""
    verifyNote = null
    form = form.copy(phone = "")
  }

  fun placeOrder() {
    if (placing) return
    val page = store.valueOrNull() ?: return
    val slug = storeSlug
    val cart = cartOf(slug)
    val problems = CheckoutRules.problems(page, cart, form, saved.ticket, slotsPage(), clock())
    if (problems.isNotEmpty()) {
      placeNote = ShopperText.problem(problems.first())
      return
    }
    val ticket = saved.ticket ?: return
    if (promo.pending(cart, ticket)) {
      //  تا پیش‌نمایشِ تخفیفِ همین سبد نرسیده «ثبت» بسته است (همان وب)
      placeNote = ShopperText.promoChecking
      requotePromo()
      return
    }
    val req = CheckoutRules.request(page, cart, form, ticket.phone, lang.code, slotsPage(), promo.applied(ticket, saved.deviceId))
      .copy(campaign = campaigns[slug])
    //  CHK-19: همان بدنه ⇒ همان کلید (پیش از فرستادن نگه داشته می‌شود؛ بستنِ برنامه وسطِ کار سفارشِ دوم نمی‌سازد)
    val memo = CheckoutKeys.keyFor(saved.keys[slug], req)
    persist(saved.copy(keys = saved.keys + (slug to memo)))
    placing = true
    placeNote = ShopperText.sending
    request({ api.checkout(slug, req, memo.key, ticket.ticket) }) { r ->
      placing = false
      when (r) {
        is ShopperResult.Ok -> placed(page, slug, r.value.order)
        is ShopperResult.Fail -> rejected(slug, cart, r.error)
      }
    }
  }

  private fun placed(page: StorePage, slug: String, o: PublicOrder) {
    var s = saved.copy(
      keys = saved.keys - slug, name = form.name.trim(),
      addressText = form.addressText.trim().ifEmpty { saved.addressText }, landmark = form.landmark.trim().ifEmpty { saved.landmark },
    )
    s = ShopperMemory.withCart(s, Cart(slug))
    s = ShopperMemory.withOrder(s, SavedOrder(slug, o.token, o.orderNo, o.totalMinor, o.createdAt, page.shop.title))
    s = s.copy(bought = Refills.record(s.bought, slug, o.lines, o.createdAt))   //  MKT-06: قلم‌های پذیرفتهٔ سرور، نه سبد
    persist(s)
    syncAccount(slug)   //  سبدِ حساب هم خالی (همان وب)
    placeNote = null
    repriced = false
    form = form.copy(note = "", slotStartsAt = null, cashTenderMinor = null)
    order = Load.Ready(o)
    stack = listOf(Route.Home, Route.Shop(slug), Route.Placed(slug, o.token))
  }

  private fun rejected(slug: String, cart: Cart, e: ShopperError) {
    val after = CheckoutFlow.after(cart, e, clock())
    when (after) {
      is CheckoutFlow.After.Repriced -> {
        setCart(after.cart)
        repriced = true
        requotePromo()
      }
      is CheckoutFlow.After.PromoRejected -> {
        promo.rejected(after.status)
        requotePromo()
      }
      is CheckoutFlow.After.Unavailable -> setCart(after.cart)
      is CheckoutFlow.After.Capped -> setCart(after.cart)
      CheckoutFlow.After.Reverify -> persist(saved.copy(ticket = null))
      is CheckoutFlow.After.Slots -> {
        val f = form.fulfillment
        val cur = slots[f]?.valueOrNull()
        val fresh = after.slots
        slots = if (fresh != null && cur != null) slots + (f to Load.Ready(cur.copy(slots = fresh))) else slots - f
        form = form.copy(slotStartsAt = null)
        loadSlots()
      }
      CheckoutFlow.After.Rezone -> form = form.copy(zoneId = null)
      CheckoutFlow.After.Message -> Unit
    }
    if (slug == storeSlug) placeNote = CheckoutFlow.note(after, e)
  }

  // ── سفارش و پیگیری ──

  fun openOrder(o: SavedOrder) = go(Route.Track(o.slug, o.token))

  private fun loadOrder(slug: String, token: String, quiet: Boolean) {
    if (!quiet) {
      order = Load.Busy
      orderNote = null
    }
    request({ api.trackPage(slug, token) }) { r ->
      val here = route as? Route.Track
      if (here == null || here.token != token) return@request
      when (r) {
        is ShopperResult.Ok -> {
          order = Load.Ready(r.value.order)
          orderExtras = r.value.extras
          feedback.bind(slug, r.value.order, r.value.extras)
          deliveryPrefs.bind(r.value.order, r.value.extras.deliveryPrefs)
          poll(here, r.value.order)
        }
        is ShopperResult.Fail -> if (quiet && order is Load.Ready) orderNote = ShopperText.error(r.error) else order = Load.Failed(r.error)
      }
    }
  }

  fun refreshOrder() {
    (route as? Route.Track)?.let { loadOrder(it.slug, it.token, quiet = order is Load.Ready) }
  }

  /** فقط وقتی همین صفحه باز است و سفارش هنوز تمام نشده (بی سرکشیِ بی‌پایان) */
  private fun poll(at: Route.Track, o: PublicOrder) {
    pollJob?.cancel()
    val every = TrackRules.refreshEveryMs(o) ?: return
    pollJob = scope.launch {
      delay(every)
      if (route == at) loadOrder(at.slug, at.token, quiet = true)
    }
  }

  fun cancelOrder() = orderAction(ShopperText.cancelled) { slug, token -> api.cancel(slug, token) }

  fun payCash() = orderAction(ShopperText.payCashDone) { slug, token -> api.payCash(slug, token) }

  private fun orderAction(done: String, call: (String, String) -> ShopperResult<PublicOrder>) {
    val here = route as? Route.Track ?: return
    if (orderBusy) return
    orderBusy = true
    orderNote = null
    request({ call(here.slug, here.token) }) { r ->
      orderBusy = false
      when (r) {
        is ShopperResult.Ok -> {
          orderNote = done
          //  پاسخِ لغو/انتخاب نمای کوتاه است؛ پیگیریِ کامل (پرداخت، پله‌ها) از نو
          if (route == here) loadOrder(here.slug, here.token, quiet = true)
        }
        is ShopperResult.Fail -> orderNote = ShopperText.error(r.error)
      }
    }
  }

  // ── تنظیمات ──

  fun setLanguage(l: ShopperLang) {
    lang = l
    ShopperText.lang = l
    persist(saved.copy(lang = l.code))
  }

  fun setTheme(t: String) = persist(saved.copy(theme = t))

  /** نشانیِ سرور عوض شد: هر چه از سرورِ پیشین آمده از نو بارگیری می‌شود (سبدها با تازه‌سازی سنجیده می‌شوند) */
  fun serverChanged() {
    store = Load.Idle
    storeSlug = ""
    market = Load.Idle
    shopList = Load.Idle
    order = Load.Idle
    home()
  }

  companion object {
    /** نمایشِ شمارهٔ ذخیره‌شده به شکلِ محلی (‎+93700… ⇐ 0700…)، مثلِ `K.localPhone` ِ وب */
    fun localPhone(e164: String): String = if (e164.startsWith("+93")) "0" + e164.removePrefix("+93") else e164
  }
}

private fun Route.withSlug(from: String, to: String): Route = when (this) {
  is Route.Shop -> if (slug == from) copy(slug = to) else this
  is Route.Product -> if (slug == from) copy(slug = to) else this
  is Route.CartPage -> if (slug == from) copy(slug = to) else this
  is Route.Checkout -> if (slug == from) copy(slug = to) else this
  is Route.Placed -> if (slug == from) copy(slug = to) else this
  is Route.Track -> if (slug == from) copy(slug = to) else this
  is Route.Chat -> if (slug == from) copy(slug = to) else this
  else -> this
}
