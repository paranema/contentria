package com.contentria.media.adapter.required.persistence

import com.contentria.media.domain.image.Media
import com.contentria.media.domain.video.Video
import org.hibernate.boot.MetadataSources

/** Every entity of the context. A new entity added here is checked by both mapping tests. */
internal fun MetadataSources.withMediaEntities(): MetadataSources =
    addAnnotatedClass(Media::class.java)
        .addAnnotatedClass(Video::class.java)
