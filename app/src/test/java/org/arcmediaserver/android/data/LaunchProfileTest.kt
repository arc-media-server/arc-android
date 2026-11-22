package org.arcmediaserver.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchProfileTest {
    private val alex = Profile("a", "Alex")
    private val jordan = Profile("b", "Jordan")
    private val household = AuthStatus(signedIn = true, profile = alex, profiles = listOf(alex, jordan))

    @Test fun freshLaunchAsksButResumeKeepsCurrentProfile() {
        assertNull(launchProfile(household, null, fresh = true))
        assertEquals(alex, launchProfile(household, jordan.id, fresh = false))
    }

    @Test fun automaticSelectionCanOpenAnotherProfile() {
        assertEquals(jordan, launchProfile(household, jordan.id, fresh = true))
    }

    @Test fun pinnedAndDeletedSelectionsFallBackToPicker() {
        val locked = jordan.copy(hasPin = true)
        assertNull(launchProfile(household.copy(profile = locked, profiles = listOf(alex, locked)), locked.id, fresh = true))
        assertNull(launchProfile(household.copy(profiles = listOf(alex)), jordan.id, fresh = true))
    }

    @Test fun keepsSingleUnlockedProfileAndFriendBehavior() {
        assertEquals(alex, launchProfile(household.copy(profiles = listOf(alex)), null, fresh = true))
        assertNull(launchProfile(household.copy(profile = null, profiles = listOf(alex)), null, fresh = true))
        assertEquals(alex, launchProfile(household.copy(friend = true, profiles = emptyList()), null, fresh = true))
    }
}
