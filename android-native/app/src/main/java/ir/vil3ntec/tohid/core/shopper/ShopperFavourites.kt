package ir.vil3ntec.tohid.core.shopper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** دکانِ دلخواه — نام و رنگ برای نمایش بی اینترنت */
@Serializable
data class FavouriteShop(val slug: String, val title: String = "", val accent: String = "", val addedAt: Long = 0)

/**
 *  دکان‌های دلخواهِ همین دستگاه (قلبِ کارتِ دکان). ⛔ فقط محلی: سرور فهرستی از
 *  دلخواه‌ها نگه نمی‌دارد؛ فقط هنگامِ خواندنِ فهرست، تا ۲۰ تای آن با `first`
 *  می‌رود تا همان‌ها اول بیایند.
 */
class ShopperFavourites(
  private val storage: ShopperStorage,
  private val now: () -> Long = { System.currentTimeMillis() },
) {
  private val serializer = ListSerializer(FavouriteShop.serializer())
  private val list = MutableStateFlow(storage.load(ShopperStorage.FAVOURITES, serializer).orEmpty().filter { it.slug.isNotBlank() })

  /** تازه‌ترین اول */
  val state: StateFlow<List<FavouriteShop>> = list.asStateFlow()

  fun contains(slug: String): Boolean = list.value.any { it.slug == slug }

  fun slugs(): List<String> = list.value.map { it.slug }

  /** @return حالا دلخواه است یا نه */
  @Synchronized
  fun toggle(slug: String, title: String, accent: String = ""): Boolean {
    val on = !contains(slug)
    val next = if (on) listOf(FavouriteShop(slug, title, accent, now())) + list.value
    else list.value.filterNot { it.slug == slug }
    list.value = next
    storage.save(ShopperStorage.FAVOURITES, serializer, next)
    return on
  }
}
