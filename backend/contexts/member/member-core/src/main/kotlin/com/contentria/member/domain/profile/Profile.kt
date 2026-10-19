package com.contentria.member.domain.profile

import com.contentria.member.domain.MemberSchema
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What other people see of an account: a name and a picture.
 *
 * Split from Account because the two are read by different callers for different reasons.
 * A profile is public, read constantly (every post, every blog header) and needs no
 * authorisation. An account is private, read once per login, and carries the email address
 * and role set. Keeping them in one table is what made rendering a post's author load an
 * email and join two authority tables, and what made a suspended author's published posts
 * fail to render at all.
 *
 * Single-entity aggregate. It has no members and no invariants beyond the display name.
 */
@Entity
@Table(name = "profiles", schema = MemberSchema.NAME)
class Profile private constructor(
    id: UUID,
    displayName: String,
    pictureUrl: String?,
) : AuditableEntity() {

    /**
     * The same value as the owning account's id.
     *
     * A profile has no lifecycle of its own, and every other context already stores an
     * account id as its author or owner reference. Sharing the identifier means those
     * contexts can read a profile directly from the id they hold, with no lookup table and
     * no second identifier to carry around.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    /** Reads better than [id] at a call site that is resolving an author or an owner. */
    val accountId: UUID
        get() = id

    @Column(name = "display_name", nullable = false, unique = true, length = DISPLAY_NAME_MAX_LENGTH)
    var displayName: String = displayName
        protected set

    @Column(name = "picture_url", length = PICTURE_URL_MAX_LENGTH)
    var pictureUrl: String? = pictureUrl
        protected set

    /**
     * When the owner withdrew, or null. The profile outlives the account so that posts and
     * comments still resolve an author; the name and picture stay (01-rules 흐름 5, 탈퇴한 회원의
     * 프로필). How a withdrawn author is shown is the screen's decision.
     */
    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null
        protected set

    val isWithdrawn: Boolean
        get() = withdrawnAt != null

    /** Idempotent: a second call keeps the first time. */
    fun markWithdrawn(at: Instant) {
        if (withdrawnAt == null) withdrawnAt = at
    }

    fun rename(newDisplayName: String) {
        displayName = normalizeDisplayName(newDisplayName)
    }

    fun changePicture(newPictureUrl: String?) {
        pictureUrl = normalizePictureUrl(newPictureUrl)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Profile && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val DISPLAY_NAME_MIN_LENGTH = 2
        const val DISPLAY_NAME_MAX_LENGTH = 50
        const val PICTURE_URL_MAX_LENGTH = 2048

        /**
         * [accountId] becomes this profile's identifier. The caller is responsible for
         * creating the account first: nothing here can check that it exists, and there is no
         * foreign key from `profiles.id` to `accounts.id` either, because the two are separate
         * aggregates. The sign-up use case creates both in one transaction.
         */
        fun create(
            accountId: UUID,
            displayName: String,
            pictureUrl: String? = null,
        ): Profile = Profile(
            id = accountId,
            displayName = normalizeDisplayName(displayName),
            pictureUrl = normalizePictureUrl(pictureUrl),
        )

        private fun normalizeDisplayName(raw: String): String {
            val normalized = raw.trim()
            if (normalized.length !in DISPLAY_NAME_MIN_LENGTH..DISPLAY_NAME_MAX_LENGTH) {
                throw ProfileException.InvalidDisplayName(raw)
            }
            return normalized
        }

        /**
         * Stores absent and blank alike as null, so readers only test one thing. An
         * over-long URL is rejected rather than dropped: silently storing null would show
         * the caller a profile with no picture and no indication that anything failed.
         */
        private fun normalizePictureUrl(raw: String?): String? {
            val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (normalized.length > PICTURE_URL_MAX_LENGTH) {
                throw ProfileException.InvalidPictureUrl(normalized.length)
            }
            return normalized
        }
    }
}
