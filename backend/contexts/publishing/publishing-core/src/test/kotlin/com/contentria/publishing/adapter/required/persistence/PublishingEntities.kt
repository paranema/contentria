package com.contentria.publishing.adapter.required.persistence

import com.contentria.publishing.domain.blog.Blog
import com.contentria.publishing.domain.category.Category
import com.contentria.publishing.domain.category.CategoryTree
import com.contentria.publishing.domain.post.Post
import com.contentria.publishing.domain.post.PostSlug
import org.hibernate.boot.MetadataSources

/** Every entity of the context. A new entity added here is checked by both mapping tests. */
internal fun MetadataSources.withPublishingEntities(): MetadataSources =
    addAnnotatedClass(Blog::class.java)
        .addAnnotatedClass(CategoryTree::class.java)
        .addAnnotatedClass(Category::class.java)
        .addAnnotatedClass(Post::class.java)
        .addAnnotatedClass(PostSlug::class.java)
