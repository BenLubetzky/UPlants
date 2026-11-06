package com.uplants.favorites

import com.uplants.favorites.models.UpdateListRequest
import com.uplants.favorites.service.Checked
import com.uplants.favorites.service.ListChanges
import com.uplants.favorites.service.isNameTaken
import com.uplants.favorites.service.parsePage
import com.uplants.favorites.service.validateListName
import com.uplants.favorites.service.validateListUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidationTest {

    @Test
    fun `list names are cleaned up and limited`() {
        assertEquals("My ferns", assertIs<Checked.Valid<String>>(validateListName("  My   ferns ")).value)
        assertIs<Checked.Invalid>(validateListName("   "))
        assertIs<Checked.Valid<String>>(validateListName("a".repeat(60)))
        assertIs<Checked.Invalid>(validateListName("a".repeat(61)))
    }

    @Test
    fun `updates need at least one field`() {
        assertEquals(ListChanges("Cacti", null), assertIs<Checked.Valid<ListChanges>>(validateListUpdate(UpdateListRequest(name = " Cacti"))).value)
        assertEquals(ListChanges(null, true), assertIs<Checked.Valid<ListChanges>>(validateListUpdate(UpdateListRequest(isPublic = true))).value)
        assertIs<Checked.Invalid>(validateListUpdate(UpdateListRequest()))
        assertIs<Checked.Invalid>(validateListUpdate(UpdateListRequest(name = "")))
    }

    @Test
    fun `duplicate names ignore case, except the list being renamed`() {
        val mine = listOf("1" to "Ferns", "2" to "Cacti")
        assertTrue(isNameTaken("ferns", mine))
        assertFalse(isNameTaken("Orchids", mine))
        assertFalse(isNameTaken("FERNS", mine, exceptListId = "1")) // renaming "Ferns" to "FERNS"
        assertTrue(isNameTaken("cacti", mine, exceptListId = "1"))
    }

    @Test
    fun `paging has limits`() {
        assertEquals(20 to 0, assertIs<Checked.Valid<Pair<Int, Int>>>(parsePage(null, null)).value)
        assertIs<Checked.Invalid>(parsePage("0", null))
        assertIs<Checked.Invalid>(parsePage(null, "-1"))
    }
}
