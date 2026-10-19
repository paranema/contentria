package com.contentria.member.application.provided

import java.util.UUID

interface GetMyProfileQuery {
    fun get(accountId: UUID): MyProfile
}

interface RenameProfileUseCase {
    fun rename(accountId: UUID, displayName: String): MyProfile
}

/** What the owner sees of themselves. Includes the email address; nobody else's view does. */
data class MyProfile(
    val accountId: UUID,
    val email: String,
    val displayName: String,
    val pictureUrl: String?,
)
