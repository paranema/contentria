package com.contentria.publishing.domain.post

import com.contentria.publishing.domain.PublishingSchema
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * A slug a post has had, its current one included. A member of [Post].
 *
 * Carries the blog id so that one unique constraint, `(blog_id, slug)`, keeps current and old
 * slugs apart across every post of the blog (02-implementation, DB 제약).
 */
@Entity
@Table(name = "post_slugs", schema = PublishingSchema.NAME)
class PostSlug internal constructor(
    post: Post,
    value: String,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = Ids.newId()

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    internal val post: Post = post

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = post.blogId

    @Column(name = "slug", nullable = false, updatable = false, length = Post.SLUG_MAX_LENGTH)
    val value: String = value

    override fun equals(other: Any?): Boolean =
        this === other || (other is PostSlug && id == other.id)

    override fun hashCode(): Int = id.hashCode()
}
