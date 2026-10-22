package com.contentria.member.application.provided

import java.util.UUID

interface GetMyProfileQuery {
    fun get(accountId: UUID): MyProfile
}

interface RenameProfileUseCase {
    fun rename(accountId: UUID, displayName: String): MyProfile
}

/**
 * 흐름 5, 공개 범위: anyone may see a member's profile. For the screens that show who wrote a post
 * or a comment; they ask for a page's worth of members at once.
 */
interface GetPublicProfilesQuery {
    /** The profiles of [accountIds] that exist, in no particular order. */
    fun get(accountIds: Set<UUID>): List<PublicProfile>
}

/** What anyone sees of a member: no email address, account status or role. */
data class PublicProfile(
    val accountId: UUID,
    val displayName: String,
    val pictureUrl: String?,
)

/** What the owner sees of themselves. Includes the email address; nobody else's view does. */
data class MyProfile(
    val accountId: UUID,
    val email: String,
    val displayName: String,
    val pictureUrl: String?,
)
