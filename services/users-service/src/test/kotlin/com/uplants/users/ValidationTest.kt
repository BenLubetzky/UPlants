package com.uplants.users

import com.uplants.users.models.UpdateProfileRequest
import com.uplants.users.service.Checked
import com.uplants.users.service.escapeLike
import com.uplants.users.service.parsePage
import com.uplants.users.service.searchFilter
import com.uplants.users.service.validateInterests
import com.uplants.users.service.validateProfileUpdate
import com.uplants.users.service.validateSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidationTest {

    @Test
    fun `like wildcards are escaped`() {
        assertEquals("fern", escapeLike("fern"))
        assertEquals("plant\\_lover", escapeLike("plant_lover"))
        assertEquals("100\\%", escapeLike("100%"))
        assertEquals("ab", escapeLike("a*b"))
        assertEquals("a\\\\b", escapeLike("a\\b"))
    }

    @Test
    fun `search filter quotes the pattern`() {
        assertEquals("(username.ilike.\"*fern*\",display_name.ilike.\"*fern*\")", searchFilter("fern"))
        // commas, parentheses and quotes can't break out of the filter
        assertEquals(
            "(username.ilike.\"*a,b) \\\"c*\",display_name.ilike.\"*a,b) \\\"c*\")",
            searchFilter("a,b) \"c"),
        )
    }

    @Test
    fun `search text is required and limited`() {
        assertEquals("fern", assertIs<Checked.Valid<String>>(validateSearch("  fern ")).value)
        assertIs<Checked.Invalid>(validateSearch(null))
        assertIs<Checked.Invalid>(validateSearch("   "))
        assertIs<Checked.Invalid>(validateSearch("***"))
        assertIs<Checked.Invalid>(validateSearch("a".repeat(51)))
    }

    @Test
    fun `profile update trims, clears empty fields and checks length`() {
        val both = assertIs<Checked.Valid<Map<String, String?>>>(validateProfileUpdate(UpdateProfileRequest(" Ana ", "")))
        assertEquals(mapOf("display_name" to "Ana", "bio" to null), both.value)

        val onlyBio = assertIs<Checked.Valid<Map<String, String?>>>(validateProfileUpdate(UpdateProfileRequest(bio = "Ferns!")))
        assertEquals(mapOf<String, String?>("bio" to "Ferns!"), onlyBio.value)

        assertIs<Checked.Invalid>(validateProfileUpdate(UpdateProfileRequest()))
        assertIs<Checked.Invalid>(validateProfileUpdate(UpdateProfileRequest(bio = "a".repeat(501))))
        assertIs<Checked.Invalid>(validateProfileUpdate(UpdateProfileRequest(displayName = "a".repeat(51))))
    }

    @Test
    fun `interests are deduplicated and limited`() {
        assertEquals(listOf(1, 2), assertIs<Checked.Valid<List<Int>>>(validateInterests(listOf(1, 2, 1))).value)
        assertEquals(emptyList(), assertIs<Checked.Valid<List<Int>>>(validateInterests(emptyList())).value)
        assertIs<Checked.Invalid>(validateInterests(listOf(0)))
        assertIs<Checked.Invalid>(validateInterests((1..21).toList()))
    }

    @Test
    fun `paging has limits`() {
        assertEquals(20 to 0, assertIs<Checked.Valid<Pair<Int, Int>>>(parsePage(null, null)).value)
        assertIs<Checked.Invalid>(parsePage("0", null))
        assertIs<Checked.Invalid>(parsePage("51", null))
        assertIs<Checked.Invalid>(parsePage(null, "-1"))
    }
}
