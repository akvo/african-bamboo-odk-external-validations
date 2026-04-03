package org.akvo.afribamodkvalidator.data

import org.akvo.afribamodkvalidator.data.repository.AppUpdateRepository.Companion.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    @Test
    fun `newer major version is detected`() {
        assertTrue(isNewerVersion("2.0", "1.6"))
    }

    @Test
    fun `newer minor version is detected`() {
        assertTrue(isNewerVersion("1.7", "1.6"))
    }

    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewerVersion("1.6.1", "1.6"))
    }

    @Test
    fun `same version returns false`() {
        assertFalse(isNewerVersion("1.6", "1.6"))
    }

    @Test
    fun `older version returns false`() {
        assertFalse(isNewerVersion("1.5", "1.6"))
    }

    @Test
    fun `older major version returns false`() {
        assertFalse(isNewerVersion("1.9.9", "2.0"))
    }

    @Test
    fun `handles v prefix on remote`() {
        assertTrue(isNewerVersion("v1.7", "1.6"))
    }

    @Test
    fun `handles v prefix on both`() {
        assertTrue(isNewerVersion("v2.0", "v1.6"))
    }

    @Test
    fun `handles pre-release suffix`() {
        assertTrue(isNewerVersion("1.7-beta", "1.6"))
    }

    @Test
    fun `handles different segment lengths`() {
        assertTrue(isNewerVersion("2.0", "1.9.9"))
        assertTrue(isNewerVersion("1.6.0.1", "1.6"))
    }

    @Test
    fun `equal with different segment lengths returns false`() {
        assertFalse(isNewerVersion("1.6.0", "1.6"))
        assertFalse(isNewerVersion("1.6", "1.6.0"))
    }

    @Test
    fun `handles empty segments gracefully`() {
        assertFalse(isNewerVersion("", "1.6"))
        assertTrue(isNewerVersion("1.6", ""))
    }
}
