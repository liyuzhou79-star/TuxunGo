package com.tuxungo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update check lives or dies by this compare: segments must be
 * compared as numbers (2.10 > 2.9), missing segments count as 0,
 * and "newer" is strict.
 */
class UpdateCheckerTest {

    @Test
    fun plainUpgrade() {
        assertTrue(UpdateChecker.isNewer("2.46.0", "2.45.0"))
        assertTrue(UpdateChecker.isNewer("3.0.0", "2.99.9"))
    }

    @Test
    fun numericNotLexical() {
        assertTrue(UpdateChecker.isNewer("2.10.0", "2.9.0"))
        assertFalse(UpdateChecker.isNewer("2.9.0", "2.10.0"))
    }

    @Test
    fun equalIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("2.45.0", "2.45.0"))
        assertFalse(UpdateChecker.isNewer("2.45.0", "2.46.0"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertFalse(UpdateChecker.isNewer("2.45", "2.45.0"))
        assertTrue(UpdateChecker.isNewer("2.45.1", "2.45"))
    }
}
