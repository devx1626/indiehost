package com.indiehost.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indiehost.detect.Engine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Written for a device or emulator. It is not run in the build container
 * because `adb` is unavailable there; do not report it as passing until it
 * has actually run.
 */
@RunWith(AndroidJUnit4::class)
class GameDaoTest {

    private lateinit var database: IndieHostDatabase
    private lateinit var dao: GameDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IndieHostDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.games()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertFindReplaceAndRemove() {
        val first = entity("scan-1", title = "Zeta", cover = "one.webp")
        dao.upsert(first)
        assertEquals(first, dao.find("scan-1"))

        val updated = first.copy(title = "Alpha", coverPath = "two.webp", linkedShortcutId = "game-9")
        dao.upsert(updated)
        assertEquals(updated, dao.find("scan-1"))
        assertEquals(listOf(updated), dao.all())

        dao.delete("scan-1")
        assertNull(dao.find("scan-1"))
    }

    private fun entity(id: String, title: String, cover: String?) = GameEntity(
        id = id,
        title = title,
        folderPath = "content://tree/$id",
        engine = Engine.GAMEMAKER_VM,
        wadVersion = 16,
        coverPath = cover,
        addedAt = 1L,
        source = GameSource.SCANNED,
        linkedShortcutId = null,
        treeUri = "content://tree",
        runnable = true,
        blocker = null,
    )
}
