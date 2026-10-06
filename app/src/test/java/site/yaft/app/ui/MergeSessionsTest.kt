package site.yaft.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import site.yaft.app.data.RemoteSession
import site.yaft.app.data.SessionMeta
import site.yaft.app.track.Category

class MergeSessionsTest {
    private fun local(id: String, at: Long, category: Category = Category.WINGFOIL) =
        SessionMeta(id, category, at, "UTC", 60.0, 100.0, 50.0, 20.0, 60, uploadedAt = null)
    private fun remote(id: String, at: Long, category: Category = Category.WINGFOIL) =
        RemoteSession(id, category, at, "", "UTC", null, null, 60.0, 100.0, 50.0, 20.0, "", emptyList(), emptyList())

    @Test
    fun mergesByIdNewestFirst() {
        val merged = mergeSessions(
            local = listOf(local("a", 3_000), local("b", 1_000)),
            remote = listOf(remote("c", 4_000), remote("a", 3_000), remote("d", 2_000)),
        )
        assertEquals(listOf("c", "a", "d", "b"), merged.map { it.id })
        val a = merged[1]
        assertEquals(true, a.local != null && a.remote != null)
    }

    @Test
    fun yaftsCategoryWinsForUploadedSessions() {
        // The rider changed the category on the website after uploading.
        val merged = mergeSessions(listOf(local("a", 1, Category.WINGFOIL)), listOf(remote("a", 1, Category.PARAWING)))
        assertEquals(Category.PARAWING, merged.single().category)
    }
}
