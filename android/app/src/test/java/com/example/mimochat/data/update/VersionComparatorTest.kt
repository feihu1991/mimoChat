package com.example.mimochat.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun `newer semantic version is detected`() {
        assertTrue(VersionComparator.isNewer("v1.1.0", "1.0.9"))
    }

    @Test
    fun `equal version is not newer`() {
        assertFalse(VersionComparator.isNewer("1.1.0", "v1.1.0"))
    }

    @Test
    fun `numeric parts are compared numerically`() {
        assertTrue(VersionComparator.isNewer("1.10.0", "1.9.9"))
    }

    @Test
    fun `stable release is newer than prerelease`() {
        assertTrue(VersionComparator.isNewer("1.2.0", "1.2.0-beta"))
    }

    @Test
    fun `prerelease is not newer than matching stable release`() {
        assertFalse(VersionComparator.isNewer("1.2.0-beta", "1.2.0"))
    }
}
