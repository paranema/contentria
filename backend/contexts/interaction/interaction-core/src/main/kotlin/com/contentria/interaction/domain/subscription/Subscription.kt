package com.contentria.interaction.domain.subscription

import com.contentria.interaction.domain.InteractionSchema
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.NamedQuery
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * A member following a blog (docs/contexts/interaction/01-rules.md, 흐름 1). The blog, not the
 * person: the same member's other blogs are subscribed to separately.
 *
 * Whether the blog exists and whose it is are publishing's facts; the use case asks before
 * creating one. One subscription per member and blog is the unique constraint's.
 */
@Entity
@NamedQuery(
    name = "Subscription.countByBlogs",
    query = "select s.blogId as id, count(s) as total from Subscription s where s.blogId in ?1 group by s.blogId",
)
// A page of GetSubscribersQuery, in id order after the previous page's last id. The limit is its PAGE_SIZE.
@NamedQuery(
    name = "Subscription.findSubscriberIds",
    query = "select s.subscriberId from Subscription s where s.blogId = ?1 and s.subscriberId > ?2 order by s.subscriberId limit 1000",
)
@Table(
    name = "subscriptions",
    schema = InteractionSchema.NAME,
    uniqueConstraints = [UniqueConstraint(name = "uq_subscriptions_subscriber_blog", columnNames = ["subscriber_id", "blog_id"])],
)
class Subscription private constructor(id: UUID, subscriberId: UUID, blogId: UUID) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @Column(name = "subscriber_id", nullable = false, updatable = false)
    val subscriberId: UUID = subscriberId

    @Column(name = "blog_id", nullable = false, updatable = false)
    val blogId: UUID = blogId

    override fun equals(other: Any?): Boolean =
        this === other || (other is Subscription && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun of(subscriberId: UUID, blogId: UUID): Subscription = Subscription(Ids.newId(), subscriberId, blogId)
    }
}
