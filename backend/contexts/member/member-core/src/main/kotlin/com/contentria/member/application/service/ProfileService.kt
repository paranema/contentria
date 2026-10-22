package com.contentria.member.application.service

import com.contentria.member.application.provided.GetMyProfileQuery
import com.contentria.member.application.provided.GetPublicProfilesQuery
import com.contentria.member.application.provided.MemberUseCaseException
import com.contentria.member.application.provided.MyProfile
import com.contentria.member.application.provided.PublicProfile
import com.contentria.member.application.provided.RenameProfileUseCase
import com.contentria.member.application.required.AccountRepository
import com.contentria.member.application.required.ProfileRepository
import com.contentria.member.domain.profile.DisplayNameGenerator
import com.contentria.member.domain.profile.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** docs/contexts/member/01-rules.md, 흐름 5. 프로필 */
@Service
class ProfileService(
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val displayNames: DisplayNameGenerator,
) : GetMyProfileQuery, RenameProfileUseCase, GetPublicProfilesQuery {

    /**
     * Every new member gets a generated name, social sign-ups included: a provider's name is the
     * person's real name (01-rules, 표시 이름 자동 생성).
     *
     * Checked before insert so a clash is retried with a new candidate. Two concurrent sign-ups
     * drawing the same name still race; the loser's sign-up fails on the unique constraint. With
     * about 5.6 million combinations that is accepted rather than handled.
     */
    fun createFor(accountId: UUID): Profile {
        repeat(MAX_NAME_ATTEMPTS) {
            val candidate = displayNames.generate()
            if (!profiles.existsByDisplayName(candidate)) {
                return profiles.save(Profile.create(accountId, candidate))
            }
        }
        error("No free display name after $MAX_NAME_ATTEMPTS attempts")
    }

    @Transactional(readOnly = true)
    override fun get(accountId: UUID): MyProfile = load(accountId)

    @Transactional(readOnly = true)
    override fun get(accountIds: Set<UUID>): List<PublicProfile> =
        if (accountIds.isEmpty()) emptyList()
        else profiles.findByIdIn(accountIds).map { PublicProfile(it.accountId, it.displayName, it.pictureUrl) }

    @Transactional
    override fun rename(accountId: UUID, displayName: String): MyProfile {
        val profile = profiles.findById(accountId) ?: error("Account $accountId has no profile")

        // Checked before the entity changes: once it is dirty, the query below would flush it
        // first and find the profile itself. Profile normalises a name by trimming it.
        val requested = displayName.trim()
        if (requested != profile.displayName && profiles.existsByDisplayName(requested)) {
            throw MemberUseCaseException.DisplayNameTaken()
        }
        profile.rename(displayName)
        return load(accountId)
    }

    private fun load(accountId: UUID): MyProfile {
        val account = accounts.findById(accountId) ?: error("Unknown account $accountId")
        val profile = profiles.findById(accountId) ?: error("Account $accountId has no profile")
        return MyProfile(account.id, account.email, profile.displayName, profile.pictureUrl)
    }

    private companion object {
        const val MAX_NAME_ATTEMPTS = 5
    }
}
