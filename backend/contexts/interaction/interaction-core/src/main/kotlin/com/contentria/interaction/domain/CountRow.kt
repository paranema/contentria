package com.contentria.interaction.domain

import java.util.UUID

/** One group of a count query: the id counted by, and how many. Filled from the entities' named queries. */
interface CountRow {
    val id: UUID
    val total: Long
}
