package com.shindo91.trainerbattle.data

import android.content.Context
import android.util.Log
import com.shindo91.trainerbattle.core.economy.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Stores the player profile as JSON in app-private storage. */
class SaveRepository(context: Context) {
    private val file = File(context.filesDir, "profile.json")

    suspend fun load(): PlayerProfile = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext PlayerProfile()
        runCatching { PlayerProfile.fromJson(file.readText()) }
            .onFailure { Log.e(TAG, "Corrupt save, starting fresh", it) }
            .getOrDefault(PlayerProfile())
    }

    suspend fun save(profile: PlayerProfile) = withContext(Dispatchers.IO) {
        // Write to a temp file first so a crash mid-write never corrupts the save.
        val tmp = File(file.parentFile, "profile.json.tmp")
        tmp.writeText(profile.toJson())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val TAG = "SaveRepository"
    }
}
