package com.contentria.notification.infra.persistence

import com.contentria.notification.domain.notification.Notification
import org.hibernate.boot.MetadataSources

/** Every entity of the context. A new entity added here is checked by both mapping tests. */
internal fun MetadataSources.withNotificationEntities(): MetadataSources =
    addAnnotatedClass(Notification::class.java)
