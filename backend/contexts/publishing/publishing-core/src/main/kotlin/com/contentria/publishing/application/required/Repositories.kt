package com.contentria.publishing.application.required

import com.contentria.publishing.domain.blog.Blog
import com.contentria.publishing.domain.category.CategoryTree
import com.contentria.publishing.domain.post.Post
import org.springframework.data.repository.Repository
import java.util.UUID

// Spring Data marker repositories (docs/architecture/02-internal-architecture.md, 리포지토리).
// Only the methods declared here exist.

interface BlogRepository : Repository<Blog, UUID> {
    fun save(blog: Blog): Blog
    fun findBySlug(slug: String): Blog?
    fun existsBySlug(slug: String): Boolean
    fun countByOwnerId(ownerId: UUID): Long
    fun findByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): List<Blog>
}

interface CategoryTreeRepository : Repository<CategoryTree, UUID> {
    fun save(tree: CategoryTree): CategoryTree
    fun findById(id: UUID): CategoryTree?

    /** For an edit: the version goes up on commit. The query and its lock are the entity's named query. */
    fun findForEdit(id: UUID): CategoryTree?
}

interface PostRepository : Repository<Post, UUID> {
    fun save(post: Post): Post
    fun existsByCategoryIdIn(categoryIds: Collection<UUID>): Boolean
}
