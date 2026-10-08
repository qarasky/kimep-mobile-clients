package dev.qarasky.unofficialkimep.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyPrivacyCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun removesOnlyRetiredTrackingPreferences() {
        val directory = temporaryFolder.newFolder("datastore")
        val tracking = File(directory, "kimep_analytics.preferences_pb").also { it.writeText("old identity") }
        val pending = File(directory, "kimep_analytics.preferences_pb.tmp").also { it.writeText("old identity") }
        val session = File(directory, "kimep_session.preferences_pb").also { it.writeText("session") }
        val settings = File(directory, "kimep_settings.preferences_pb").also { it.writeText("settings") }

        LegacyPrivacyCleanup.removeTrackingPreferences(temporaryFolder.root)
        LegacyPrivacyCleanup.removeTrackingPreferences(temporaryFolder.root)

        assertFalse(tracking.exists())
        assertFalse(pending.exists())
        assertTrue(session.exists())
        assertTrue(settings.exists())
    }

    @Test
    fun freshInstallWithNoTrackingFileIsSafe() {
        LegacyPrivacyCleanup.removeTrackingPreferences(temporaryFolder.root)
        assertFalse(File(temporaryFolder.root, "datastore").exists())
    }
}
