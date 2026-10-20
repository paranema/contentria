package com.contentria.interaction.application

import com.contentria.shared.exception.DomainException

/** Failures decided by a use case rather than by a single aggregate. */
sealed class InteractionException(message: String) : DomainException(message) {

    /** 01-rules 전제 4: the account is not ACTIVE, or member does not know it. */
    class NotAllowed :
        InteractionException("This account cannot interact")

    class BlogNotFound :
        InteractionException("No blog has this address")

    /** No such post, or one readers cannot see: the same answer for both (01-rules 흐름 2의 2번). */
    class PostNotFound :
        InteractionException("No such post")

    /** 01-rules 흐름 1의 3번 */
    class OwnBlog :
        InteractionException("A blog's owner does not subscribe to it")

    /** 01-rules 흐름 2의 3번 */
    class OwnPost :
        InteractionException("A post's owner does not like it")
}
