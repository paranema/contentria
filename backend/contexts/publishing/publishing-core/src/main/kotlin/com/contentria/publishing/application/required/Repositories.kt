package com.contentria.publishing.application.required

import com.contentria.publishing.domain.blog.Blog
import com.contentria.publishing.domain.category.CategoryTree
import com.contentria.publishing.domain.post.Post
import com.contentria.publishing.domain.post.PostStatus
import org.springframework.data.repository.Repository
import java.time.Instant
import java.util.UUID

// Spring Data marker repositories (docs/architecture/02-internal-architecture.md, 리포지토리).
// Only the methods declared here exist.

interface BlogRepository : Repository<Blog, UUID> {
    fun save(blog: Blog): Blog
    fun findById(id: UUID): Blog?
    fun findByIdIn(ids: Collection<UUID>): List<Blog>
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

    /** [Post]'s named query of the same name, for publishing-api. */
    fun findReferences(postIds: Collection<UUID>): List<PostReferenceRow>

    /** [Post]'s named query of the same name. Titles for screens other contexts rank posts on. */
    fun findTitles(postIds: Collection<UUID>): List<PostTitleRow>

    /** [Post]'s named query of the same name: published posts' titles and addresses, for readers' screens. */
    fun findPublishedTitles(postIds: Collection<UUID>): List<PublishedTitleRow>
    fun countByBlogIdAndStatus(blogId: UUID, status: PostStatus): Long

    /** The post [slug] leads to in the blog, whether it is the post's current slug or an old one. */
    fun findBySlugInBlog(blogId: UUID, slug: String): Post?

    // A page of [limit] rows after skipping [offset], and the count of all such rows. The queries
    // are [Post]'s named queries of the same names.

    fun findPublishedInBlog(blogId: UUID, limit: Int, offset: Int): List<PostRow>
    fun countPublishedInBlog(blogId: UUID): Long

    /** [limit] and [offset] come before [categoryIds] to keep the queries' parameter positions alike. */
    fun findPublishedInCategories(blogId: UUID, limit: Int, offset: Int, categoryIds: Collection<UUID>): List<PostRow>
    fun countPublishedInCategories(blogId: UUID, categoryIds: Collection<UUID>): Long

    fun findRecentPublished(limit: Int, offset: Int): List<PostRow>
    fun countRecentPublished(): Long

    /** The owner's list: every status, most recently changed first. */
    fun findInBlogByUpdate(blogId: UUID, limit: Int, offset: Int): List<PostRow>
    fun countInBlog(blogId: UUID): Long
}
