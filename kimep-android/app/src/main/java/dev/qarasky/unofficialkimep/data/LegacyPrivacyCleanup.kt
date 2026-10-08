package dev.qarasky.unofficialkimep.data

import java.io.File

/** Remove the retired tracking identity and consent from installations of older builds. */
internal object LegacyPrivacyCleanup {
    fun removeTrackingPreferences(filesDir: File) {
        val directory = File(filesDir, "datastore")
        for (name in listOf("kimep_analytics.preferences_pb", "kimep_analytics.preferences_pb.tmp")) {
            File(directory, name).delete()
        }
    }
}
