package com.contentria.publishing.domain.category

import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import com.contentria.publishing.domain.PublishingSchema
import java.util.UUID

/**
 * One category of a blog. A member of [CategoryTree], reached only through it; every change goes
 * through [CategoryTree.replaceWith], so the mutators here are internal.
 *
 * The parent is held as an id, not an association: both ends sit in the same aggregate, which
 * loads all of its categories at once anyway.
 */
@Entity
@Table(name = "categories", schema = PublishingSchema.NAME)
class Category internal constructor(
    id: UUID,
    tree: CategoryTree,
    name: String,
    slug: String,
    parentId: UUID?,
    displayOrder: Int,
) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tree_id", nullable = false, updatable = false)
    internal val tree: CategoryTree = tree

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    var name: String = name
        protected set

    @Column(name = "slug", nullable = false, length = SLUG_MAX_LENGTH)
    var slug: String = slug
        protected set

    /** Null for a top-level category. */
    @Column(name = "parent_id")
    var parentId: UUID? = parentId
        protected set

    /** Position among the categories with the same parent, from 0. */
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = displayOrder
        protected set

    internal fun place(name: String, parentId: UUID?, displayOrder: Int) {
        this.name = name
        this.parentId = parentId
        this.displayOrder = displayOrder
    }

    internal fun assignSlug(slug: String) {
        this.slug = slug
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Category && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val NAME_MAX_LENGTH = 30
        const val SLUG_MAX_LENGTH = 50
    }
}
