package com.contentria.analytics.infra.persistence

import com.contentria.analytics.domain.visit.Visit
import org.hibernate.boot.MetadataSources

/** Every entity of the context. Daily statistics are written by SQL and have none. */
internal fun MetadataSources.withAnalyticsEntities(): MetadataSources =
    addAnnotatedClass(Visit::class.java)
