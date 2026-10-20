package com.contentria.interaction.domain.comment

import com.contentria.shared.exception.DomainException

sealed class CommentException(message: String) : DomainException(message) {

    class InvalidBody :
        CommentException("A comment is 1-${Comment.BODY_MAX_LENGTH} characters")

    /** 01-rules 흐름 4의 1번 */
    class NotAuthor :
        CommentException("Only the comment's author can change it")

    /** 01-rules 흐름 3의 4번: replies are one level deep. */
    class ReplyToReply :
        CommentException("A reply cannot be replied to")

    /** 01-rules 흐름 3의 5번, 흐름 4의 2번 */
    class Removed :
        CommentException("The comment was deleted; only its place remains")
}
