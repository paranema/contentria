package com.contentria.publishing.application.required

import com.contentria.publishing.domain.blog.Blog
import com.contentria.publishing.domain.category.CategoryTree
import com.contentria.publishing.domain.post.Post
import org.springframework.data.repository.Repository
import java.time.Instant
import java.util.UUID

// Spring Data marker repositories (docs/architecture/02-internal-architecture.md, 리포지토리).
// Only the methods declared here exist.

interface BlogRepository : Repository<Blog, UUID> {
    fun save(blog: Blog): Blog
    fun findById(id: UUID): Blog?
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
    fun findById(id: UUID): Post?
    fun delete(post: Post)
    fun existsByCategoryIdIn(categoryIds: Collection<UUID>): Boolean


    /** [Post]'s named query of the same name. [pattern] is a LIKE pattern; [exceptPostId] keeps a post's own slugs out. */
    fun findSlugsTakenInBlog(blogId: UUID, pattern: String, exceptPostId: UUID): List<String>

    /** The post [slug] leads to in the blog, whether it is the post's current slug or an old one. */
    fun findBySlugInBlog(blogId: UUID, slug: String): Post?

    // Pages of 21 rows: 20 to show, one to tell whether a next page exists. Each starts after
    // (at, id). The queries are [Post]'s named queries of the same names.

    fun findPublishedInBlog(blogId: UUID, beforeAt: Instant, beforeId: UUID): List<PostRow>

    /** [beforeAt] and [beforeId] come before [categoryIds] to keep the queries' parameter positions alike. */
    fun findPublishedInCategories(blogId: UUID, beforeAt: Instant, beforeId: UUID, categoryIds: Collection<UUID>): List<PostRow>

    fun findRecentPublished(beforeAt: Instant, beforeId: UUID): List<PostRow>

    /** The owner's list: every status, most recently changed first. */
    fun findInBlogByUpdate(blogId: UUID, beforeAt: Instant, beforeId: UUID): List<PostRow>
}
