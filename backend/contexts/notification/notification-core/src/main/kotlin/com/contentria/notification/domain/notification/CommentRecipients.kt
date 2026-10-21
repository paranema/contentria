package com.contentria.notification.domain.notification

import java.util.UUID

/** Who hears of a comment, and as what. */
data class CommentRecipient(val accountId: UUID, val kind: NotificationKind) {
    init {
        require(kind != NotificationKind.NEW_POST) { "A comment does not make a new-post notification" }
    }
}

/**
 * docs/contexts/notification/01-rules.md, 흐름 2. Decided from the event alone: nobody hears of
 * what they did themselves, and nobody hears twice of one comment. The parent's author comes
 * first, so a blog owner whose own comment got a reply hears of the reply, not of a comment.
 */
object CommentRecipients {

    fun of(commenterId: UUID, blogOwnerId: UUID, parentCommenterId: UUID?): List<CommentRecipient> {
        val recipients = mutableListOf<CommentRecipient>()
        if (parentCommenterId != null && parentCommenterId != commenterId) {
            recipients += CommentRecipient(parentCommenterId, NotificationKind.REPLY_TO_COMMENT)
        }
        if (blogOwnerId != commenterId && recipients.none { it.accountId == blogOwnerId }) {
            recipients += CommentRecipient(blogOwnerId, NotificationKind.COMMENT_ON_POST)
        }
        return recipients
    }
}
