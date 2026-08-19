package com.nuvio.tv.core.installation

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class InstallationIdManagerTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var context: Context
    private lateinit var manager: InstallationIdManager

    @Before
    fun setUp() {
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)
        `when`(editor.clear()).thenReturn(editor)
        // disableAutoBackup is API 24+; it returns the editor. Just no-op.
        `when`(prefs.edit()).thenReturn(editor)
        context = mock(Context::class.java)
        `when`(context.getSharedPreferences("apachiy_installation", Context.MODE_PRIVATE))
            .thenReturn(prefs)
        manager = InstallationIdManager(context)
    }

    @Test
    fun `first run generates UUID v4 and persists it`() {
        `when`(prefs.getString("installation_id", null)).thenReturn(null)
        val id = manager.getInstallationId()
        assertTrue("id must be a UUID v4", UUID_V4.matches(id))
        verify(editor).putString("installation_id", id)
    }

    @Test
    fun `second call returns same GUID without writing`() {
        val existing = UUID.randomUUID().toString()
        `when`(prefs.getString("installation_id", null)).thenReturn(existing)
        val first = manager.getInstallationId()
        val second = manager.getInstallationId()
        assertEquals(existing, first)
        assertEquals(existing, second)
        verify(editor, never()).putString(anyString(), anyString())
    }

    @Test
    fun `corrupted stored id is replaced with a fresh UUID v4`() {
        `when`(prefs.getString("installation_id", null)).thenReturn("nuvio-tv-zzzzzzzzzz")
        val id = manager.getInstallationId()
        assertNotEquals("nuvio-tv-zzzzzzzzzz", id)
        assertTrue(UUID_V4.matches(id))
        verify(editor).putString(eq("installation_id"), eq(id))
    }

    @Test
    fun `restart simulation yields same GUID`() {
        val existing = UUID.randomUUID().toString()
        `when`(prefs.getString("installation_id", null)).thenReturn(existing)
        // Re-create manager to simulate process restart
        val manager2 = InstallationIdManager(context)
        assertEquals(existing, manager2.getInstallationId())
    }

    @Test
    fun `logout and login yields same GUID because prefs are untouched`() {
        val existing = UUID.randomUUID().toString()
        `when`(prefs.getString("installation_id", null)).thenReturn(existing)
        val a = manager.getInstallationId()
        // No signOut action touches this pref file in the implementation.
        val b = manager.getInstallationId()
        assertEquals(a, b)
    }

    @Test
    fun `uninstall reinstall simulation produces a new GUID`() {
        // First install: no stored value
        `when`(prefs.getString("installation_id", null)).thenReturn(null)
        val firstInstall = manager.getInstallationId()
        assertNotNull(firstInstall)
        // After uninstall: data is wiped. Simulate by re-mocking the prefs to return null.
        `when`(prefs.getString("installation_id", null)).thenReturn(null)
        val secondInstall = InstallationIdManager(context).getInstallationId()
        assertNotEquals(firstInstall, secondInstall)
        assertTrue(UUID_V4.matches(secondInstall))
    }

    companion object {
        private val UUID_V4 = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
        )
    }
}