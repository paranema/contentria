package com.contentria.interaction.infra.persistence

import com.contentria.interaction.domain.comment.Comment
import com.contentria.interaction.domain.like.Like
import com.contentria.interaction.domain.subscription.Subscription
import org.hibernate.boot.MetadataSources

/** Every entity of the context. A new entity added here is checked by both mapping tests. */
internal fun MetadataSources.withInteractionEntities(): MetadataSources =
    addAnnotatedClass(Subscription::class.java)
        .addAnnotatedClass(Like::class.java)
        .addAnnotatedClass(Comment::class.java)
