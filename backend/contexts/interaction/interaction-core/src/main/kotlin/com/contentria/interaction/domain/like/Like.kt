package com.contentria.interaction.domain.like

import com.contentria.interaction.domain.InteractionSchema
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * A member's like on a post (docs/contexts/interaction/01-rules.md, 흐름 2). One per member and
 * post, by the unique constraint. Carries the blog so later per-blog questions need not ask
 * publishing again (02-implementation, 애그리거트 경계).
 */
@Entity
@Table(name = "likes", schema = InteractionSchema.NAME)
class Like private constructor(id: UUID, accountId: UUID, postId: UUID, blogId: UUID) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "account_id", nullable = false, updatable = false)
    val accountId: UUID = accountId

    @Column(name = "post_id", nullable = false, updatable = false)
    val postId: UUID = postId

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = blogId

    override fun equals(other: Any?): Boolean =
        this === other || (other is Like && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun of(accountId: UUID, postId: UUID, blogId: UUID): Like = Like(Ids.newId(), accountId, postId, blogId)
    }
}
