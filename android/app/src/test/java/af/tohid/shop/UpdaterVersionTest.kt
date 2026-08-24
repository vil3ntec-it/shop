package af.tohid.shop

import af.tohid.shop.util.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterVersionTest {

    @Test fun newerPatchIsDetected() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
    }

    @Test fun newerMinorIsDetected() {
        assertTrue(Updater.isNewer("1.1.0", "1.0.9"))
    }

    @Test fun sameVersionIsNotNewer() {
        assertFalse(Updater.isNewer("1.2.3", "1.2.3"))
    }

    @Test fun olderVersionIsNotNewer() {
        assertFalse(Updater.isNewer("1.0.0", "1.0.1"))
    }

    @Test fun shorterVersionComparesAsZeros() {
        assertTrue(Updater.isNewer("1.1", "1.0.9"))
        assertFalse(Updater.isNewer("1.0", "1.0.0"))
    }
}
