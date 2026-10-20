package com.contentria.interaction.presentation.web

import com.contentria.interaction.api.GetInteractionCountsQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Counts anyone may see (01-rules, 공개 범위), by id batch so a list asks once. Open to readers who
 * are not signed in; the host's filter chain allows `GET /interactions/counts`.
 */
@RestController
class InteractionCountController(private val counts: GetInteractionCountsQuery) {

    @GetMapping("/interactions/counts")
    fun counts(
        @RequestParam(required = false, defaultValue = "") postIds: Set<UUID>,
        @RequestParam(required = false, defaultValue = "") blogIds: Set<UUID>,
    ): InteractionCounts = InteractionCounts(
        likes = counts.likeCounts(postIds.limited()),
        subscribers = counts.subscriberCounts(blogIds.limited()),
    )
}

data class InteractionCounts(val likes: Map<UUID, Long>, val subscribers: Map<UUID, Long>)

/** One list page asks for at most this many ids at once. */
internal const val MAX_IDS = 100

internal fun Set<UUID>.limited(): Set<UUID> {
    if (size > MAX_IDS) throw TooManyIds()
    return this
}

class TooManyIds : RuntimeException("At most $MAX_IDS ids per request")
