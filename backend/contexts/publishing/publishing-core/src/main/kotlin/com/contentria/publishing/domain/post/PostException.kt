package com.contentria.publishing.domain.post

import com.contentria.shared.exception.DomainException

sealed class PostException(message: String) : DomainException(message) {

    class InvalidTitle :
        PostException("A post title is 1-${Post.TITLE_MAX_LENGTH} characters")

    class InvalidBody(length: Int) :
        PostException("A post body is at most ${Post.BODY_MAX_LENGTH} characters, was $length")

    class InvalidSummary(length: Int) :
        PostException("A post summary is at most ${PostSummary.MAX_LENGTH} characters, was $length")

    class InvalidSlug(value: String) :
        PostException("Not a usable post slug: $value")

    /** 01-rules 흐름 4: the transition is not allowed from the current status. */
    class StatusTransitionNotAllowed(from: PostStatus, action: String) :
        PostException("A $from post cannot $action")

    /** 01-rules 글 slug: only a draft's slug follows its title. */
    class SlugFollowsTitleOnlyInDraft :
        PostException("A published post's slug changes only when the author changes it")
}
