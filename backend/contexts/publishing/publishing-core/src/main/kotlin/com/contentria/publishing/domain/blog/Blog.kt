package com.contentria.publishing.domain.blog

import com.contentria.publishing.domain.PublishingSchema
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.Locale
import java.util.UUID

/**
 * One blog and who owns it (docs/contexts/publishing/01-rules.md, 흐름 1).
 *
 * The slug is the blog's address (`/@{slug}`) and never changes; the owner never changes either.
 * The rules that need other blogs (the slug is free, the owner has fewer than three) are the
 * use case's, backed by the unique constraint on the slug.
 */
@Entity
@Table(name = "blogs", schema = PublishingSchema.NAME)
class Blog private constructor(
    id: UUID,
    ownerId: UUID,
    slug: String,
    title: String,
    description: String?,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    /** The member's account id. The only thing publishing knows about the member. */
    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID = ownerId

    @Column(name = "slug", nullable = false, updatable = false, unique = true, length = SLUG_MAX_LENGTH)
    val slug: String = slug

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    var title: String = title
        protected set

    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    var description: String? = description
        protected set

    fun isOwnedBy(accountId: UUID): Boolean = ownerId == accountId

    /** Title and description are the only things the owner can change. */
    fun changeSettings(title: String, description: String?) {
        this.title = normalizeTitle(title)
        this.description = normalizeDescription(description)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Blog && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {

        const val MAX_PER_OWNER = 3
        const val SLUG_MIN_LENGTH = 3
        const val SLUG_MAX_LENGTH = 30
        const val TITLE_MAX_LENGTH = 50
        const val DESCRIPTION_MAX_LENGTH = 300

        /** Latin letters and digits only, unlike post slugs: the blog address is typed, not derived. */
        private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

        /**
         * Addresses members cannot take (docs/contexts/publishing/01-rules.md, 예약 slug): the
         * platform's own accounts, and words a visitor would read as the platform speaking.
         */
        val RESERVED_SLUGS: Set<String> = setOf(
            // 운영과 공식
            "admin", "administrator", "root", "system", "staff", "official", "team", "moderator",
            "notice", "notices", "announcement", "announcements", "news", "event", "events",
            "support", "help", "faq", "contact", "security", "abuse", "report", "feedback",
            // 서비스 경로와 계정
            "api", "www", "app", "auth", "login", "logout", "signin", "signup", "register",
            "account", "settings", "me", "profile", "dashboard", "search", "explore", "home",
            "about", "terms", "privacy", "policy", "status", "blog", "blogs", "post", "posts",
            "mail", "email", "static", "assets", "cdn", "media",
            // 값처럼 보이는 말
            "null", "undefined", "anonymous",
        )

        /** Any address naming the platform itself. */
        private const val PLATFORM_NAME = "contentria"

        fun isReserved(slug: String): Boolean = slug in RESERVED_SLUGS || PLATFORM_NAME in slug

        fun create(ownerId: UUID, slug: String, title: String, description: String? = null): Blog =
            Blog(
                id = Ids.newId(),
                ownerId = ownerId,
                slug = normalizeSlug(slug),
                title = normalizeTitle(title),
                description = normalizeDescription(description),
            )

        /** The same normalisation the use case applies before asking whether the slug is free. */
        fun normalizeSlug(raw: String): String {
            val slug = raw.trim().lowercase(Locale.ROOT)
            if (slug.length !in SLUG_MIN_LENGTH..SLUG_MAX_LENGTH || !SLUG.matches(slug)) throw BlogException.InvalidSlug(raw)
            if (isReserved(slug)) throw BlogException.ReservedSlug(slug)
            return slug
        }

        private fun normalizeTitle(raw: String): String {
            val title = raw.trim()
            if (title.length !in 1..TITLE_MAX_LENGTH) throw BlogException.InvalidTitle()
            return title
        }

        /** Absent and blank alike become null. */
        private fun normalizeDescription(raw: String?): String? {
            val description = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (description.length > DESCRIPTION_MAX_LENGTH) throw BlogException.InvalidDescription(description.length)
            return description
        }
    }
}
