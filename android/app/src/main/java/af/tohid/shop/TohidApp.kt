package af.tohid.shop

import android.app.Application
import af.tohid.shop.data.db.TohidDatabase
import af.tohid.shop.data.repo.SessionStore
import af.tohid.shop.data.repo.StockRepository
import af.tohid.shop.data.sync.SyncEngine
import af.tohid.shop.data.sync.SyncScheduler

class TohidApp : Application() {

    val db by lazy { TohidDatabase.get(this) }
    val session by lazy { SessionStore(this) }
    val stock by lazy { StockRepository(db, session) }
    val sync by lazy { SyncEngine(db, session) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // همگام‌سازی دوره‌ای در پس‌زمینه، فقط وقتی اینترنت هست
        SyncScheduler.schedulePeriodic(this)
    }

    companion object {
        lateinit var instance: TohidApp
            private set
    }
}
