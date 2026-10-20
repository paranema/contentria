package com.contentria.publishing.domain

/**
 * The database schema this context owns (docs/architecture/04-persistence-conventions.md).
 * Every publishing table lives here, and nothing outside this context reads or writes it.
 */
object PublishingSchema {
    const val NAME = "publishing"
}
