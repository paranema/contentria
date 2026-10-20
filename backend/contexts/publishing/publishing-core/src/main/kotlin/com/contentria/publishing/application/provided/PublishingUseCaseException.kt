package com.contentria.publishing.application.provided

import com.contentria.shared.exception.DomainException

/** Failures decided by a use case rather than by a single aggregate. */
sealed class PublishingUseCaseException(message: String) : DomainException(message) {

    /** 01-rules 전제 6: the account is not ACTIVE, or member does not know it. */
    class BloggerNotActive :
        PublishingUseCaseException("This account cannot write")

    class BlogNotFound :
        PublishingUseCaseException("No blog has this address")

    /** 01-rules 공통, 소유 확인 */
    class NotBlogOwner :
        PublishingUseCaseException("Only the blog's owner can change it")

    /** 01-rules 흐름 1의 5번 */
    class BlogSlugTaken :
        PublishingUseCaseException("Another blog uses this address")

    /** 01-rules 흐름 1의 2번 */
    class BlogLimitReached :
        PublishingUseCaseException("A member has at most three blogs")

    /** 01-rules 흐름 2의 5번 */
    class CategoriesInUse :
        PublishingUseCaseException("A category being removed still has posts")

    /** The tree changed since the editor loaded it. */
    class StaleCategoryTree :
        PublishingUseCaseException("The categories were changed elsewhere; reload and edit again")
}
