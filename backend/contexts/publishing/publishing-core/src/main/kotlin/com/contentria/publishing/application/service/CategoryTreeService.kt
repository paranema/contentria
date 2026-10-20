package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.CategoryTreeView
import com.contentria.publishing.application.provided.CategoryView
import com.contentria.publishing.application.provided.EditCategoryTreeCommand
import com.contentria.publishing.application.provided.EditCategoryTreeUseCase
import com.contentria.publishing.application.provided.GetCategoryTreeQuery
import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.BlogRepository
import com.contentria.publishing.application.required.BloggerStatusPort
import com.contentria.publishing.application.required.CategoryTreeRepository
import com.contentria.publishing.application.required.PostRepository
import com.contentria.publishing.domain.category.CategoryTree
import com.contentria.publishing.domain.category.CategoryTreeEdit
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** docs/contexts/publishing/01-rules.md, 흐름 2. 카테고리 편집 */
@Service
class CategoryTreeService(
    private val blogs: BlogRepository,
    private val trees: CategoryTreeRepository,
    private val posts: PostRepository,
    private val bloggers: BloggerStatusPort,
    private val transactions: TransactionTemplate,
) : GetCategoryTreeQuery, EditCategoryTreeUseCase {

    @Transactional(readOnly = true)
    override fun get(blogSlug: String): CategoryTreeView? =
        blogs.findBySlug(blogSlug)?.let { trees.findById(it.id) }?.view()

    override fun edit(command: EditCategoryTreeCommand): CategoryTreeView {
        if (!bloggers.canPublish(command.requesterId)) throw PublishingUseCaseException.BloggerNotActive()

        val blogId = try {
            transactions.execute { editInTransaction(command) }!!
        } catch (e: OptimisticLockingFailureException) {
            // Another save committed between this one's read and its write.
            throw PublishingUseCaseException.StaleCategoryTree()
        }
        // Read back after commit: the version is raised as the edit commits, after the edit returns.
        return transactions.execute { trees.findById(blogId)!!.view() }!!
    }

    /** Returns the blog id. */
    private fun editInTransaction(command: EditCategoryTreeCommand): UUID {
        // 1번
        val blog = blogs.findBySlug(command.blogSlug) ?: throw PublishingUseCaseException.BlogNotFound()
        if (!blog.isOwnedBy(command.requesterId)) throw PublishingUseCaseException.NotBlogOwner()

        val tree = trees.findForEdit(blog.id) ?: error("Blog ${blog.id} has no category tree")
        // The editor worked on an older tree; saving it would undo the other change.
        if (tree.version != command.expectedVersion) throw PublishingUseCaseException.StaleCategoryTree()

        val edit = CategoryTreeEdit(command.nodes)
        // 2~4번 before 5번, so the first rule the edit breaks is the one reported.
        tree.validate(edit)
        val removed = tree.categoryIdsRemovedBy(edit)
        if (removed.isNotEmpty() && posts.existsByCategoryIdIn(removed)) throw PublishingUseCaseException.CategoriesInUse()

        tree.replaceWith(edit)
        trees.save(tree)
        return blog.id
    }

    private fun CategoryTree.view() = CategoryTreeView(
        blogId = blogId,
        version = checkNotNull(version) { "A stored tree has a version" },
        categories = categories.map { CategoryView(it.id, it.name, it.slug, it.parentId, it.displayOrder) },
    )
}
