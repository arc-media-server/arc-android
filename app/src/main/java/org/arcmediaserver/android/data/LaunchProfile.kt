package org.arcmediaserver.android.data

/** The profile that can open directly. Null sends a signed-in household to its picker. */
internal fun launchProfile(status: AuthStatus, automaticId: String?, fresh: Boolean): Profile? {
    if (!fresh || status.friend) return status.profile
    if (automaticId != null) return status.profiles.find { it.id == automaticId && !it.hasPin }
    return status.profiles.singleOrNull()?.takeIf { !it.hasPin && it.id == status.profile?.id }
}
