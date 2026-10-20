package com.contentria.publishing.domain.category

import com.contentria.publishing.domain.PublishingSchema
import com.contentria.publishing.domain.slug.Slugs
import com.contentria.shared.id.Ids
import com.contentria.shared.persistence.AuditableEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.Locale
import java.util.UUID

/**
 * A blog's categories, as one aggregate (docs/contexts/publishing/02-implementation.md, 애그리거트 경계).
 *
 * Depth, sibling names and removals can only be judged against the whole tree, and the editor
 * saves the whole tree at once, so the tree is the unit of consistency rather than a category.
 * Its id is the blog's id: one tree per blog.
 *
 * Whether a removed category still has posts is not visible from here. The use case asks
 * [categoryIdsRemovedBy] first and checks the posts before calling [replaceWith].
 */
@Entity
@Table(name = "category_trees", schema = PublishingSchema.NAME)
class CategoryTree private constructor(id: UUID) : AuditableEntity() {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = id

    val blogId: UUID
        get() = id

    /** Two editors saving the same tree: the second save fails instead of silently undoing the first. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @OneToMany(mappedBy = "tree", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val categoryList: MutableList<Category> = mutableListOf()

    /** Top-level categories first, each followed by its children, in display order. */
    val categories: List<Category>
        get() {
            val byParent = categoryList.groupBy { it.parentId }
            return byParent[null].orEmpty().sortedBy { it.displayOrder }.flatMap { top ->
                listOf(top) + byParent[top.id].orEmpty().sortedBy { it.displayOrder }
            }
        }

    fun contains(categoryId: UUID): Boolean = categoryList.any { it.id == categoryId }

    /** [categoryId] and its children: what a top-level category's post list covers (01-rules 흐름 2). */
    fun withChildren(categoryId: UUID): Set<UUID> =
        categoryList.filter { it.id == categoryId || it.parentId == categoryId }.map { it.id }.toSet()

    /** The categories [edit] would remove. Asked before [replaceWith] so the use case can check for posts. */
    fun categoryIdsRemovedBy(edit: CategoryTreeEdit): Set<UUID> {
        val kept = edit.nodes.mapNotNull { existingIdOf(it.key) }.toSet()
        return categoryList.map { it.id }.filterNot { it in kept }.toSet()
    }

    /**
     * Makes the tree look like [edit]. Validates the whole edit before changing anything, so a
     * rejected edit leaves the tree as it was.
     */
    fun replaceWith(edit: CategoryTreeEdit) {
        val plan = plan(edit)

        val removed = categoryIdsRemovedBy(edit)
        categoryList.removeIf { it.id in removed }

        // Ids first, so a child can point at a parent created in the same edit.
        val idOf = plan.associate { it.key to (existingIdOf(it.key) ?: Ids.newId()) }
        val previousNames = categoryList.associate { it.id to it.name }

        plan.forEach { node ->
            val id = idOf.getValue(node.key)
            val parentId = node.parentKey?.let(idOf::getValue)
            val existing = categoryList.firstOrNull { it.id == id }
            if (existing == null) {
                categoryList.add(Category(id, this, node.name, slug = "", parentId, node.order))
            } else {
                existing.place(node.name, parentId, node.order)
            }
        }
        assignSlugs(previousNames)
    }

    /** Checks 01-rules 흐름 2의 2~4번 against the edit and the current tree. */
    private fun plan(edit: CategoryTreeEdit): List<PlannedNode> {
        val keys = mutableSetOf<String>()
        edit.nodes.forEach { if (!keys.add(it.key)) throw CategoryException.DuplicateKey(it.key) }
        val byKey = edit.nodes.associateBy { it.key }

        val planned = edit.nodes.map { node ->
            val name = node.name.trim()
            if (name.length !in 1..Category.NAME_MAX_LENGTH) throw CategoryException.InvalidName()
            node.parentKey?.let { parentKey ->
                val parent = byKey[parentKey] ?: throw parentMissing(node.key, parentKey)
                if (parent.parentKey != null) throw CategoryException.TooDeep(node.key)
            }
            node to name
        }

        planned.groupBy({ it.first.parentKey }, { it.second.lowercase(Locale.ROOT) }).values.forEach { siblings ->
            siblings.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
                ?.let { throw CategoryException.DuplicateName(it) }
        }

        val orderWithinParent = mutableMapOf<String?, Int>()
        return planned.map { (node, name) ->
            val order = orderWithinParent.merge(node.parentKey, 1, Int::plus)!! - 1
            PlannedNode(node.key, name, node.parentKey, order)
        }
    }

    /** A child of a category the edit drops is a removal that leaves children behind (흐름 2의 4번). */
    private fun parentMissing(key: String, parentKey: String): CategoryException =
        if (existingIdOf(parentKey) != null) CategoryException.ParentRemoved(key) else CategoryException.UnknownParent(key)

    /**
     * Keeps the slug of every category whose name did not change, then gives the others a slug
     * from their name that no category in the tree uses.
     */
    private fun assignSlugs(previousNames: Map<UUID, String>) {
        val (unchanged, renamed) = categories.partition { previousNames[it.id] == it.name && it.slug.isNotEmpty() }
        val taken = unchanged.map { it.slug }.toMutableSet()
        renamed.forEach { category ->
            val base = Slugs.from(category.name, fallback = "category", maxLength = Category.SLUG_MAX_LENGTH)
            val slug = Slugs.nextAvailable(base, taken, Category.SLUG_MAX_LENGTH)
            category.assignSlug(slug)
            taken += slug
        }
    }

    /** The key as one of this tree's category ids, or null for a new category. A key from another tree is new. */
    private fun existingIdOf(key: String): UUID? =
        runCatching { UUID.fromString(key) }.getOrNull()?.takeIf { id -> categoryList.any { it.id == id } }

    private data class PlannedNode(val key: String, val name: String, val parentKey: String?, val order: Int)

    override fun equals(other: Any?): Boolean =
        this === other || (other is CategoryTree && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /** An empty tree for a new blog. */
        fun createFor(blogId: UUID): CategoryTree = CategoryTree(blogId)
    }
}
