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

    /**
     * No such post, or one the requester may not see. The same answer for both, so a draft's
     * existence is not revealed (01-rules 공통, 숨긴 것의 응답).
     */
    class PostNotFound :
        PublishingUseCaseException("No such post")

    /** 01-rules 흐름 3의 3번 */
    class CategoryNotInBlog :
        PublishingUseCaseException("The category belongs to another blog")

    /** 01-rules 글 slug, 고유성: another post of the blog has or had this slug. */
    class PostSlugTaken :
        PublishingUseCaseException("Another post of this blog uses or used this address")

    class CategoryNotFound :
        PublishingUseCaseException("The blog has no category at this address")

    /** A page number below 0, or too far to count up to. */
    class InvalidPage :
        PublishingUseCaseException("The page number is not valid")

    /** The tree changed since the editor loaded it. */
    class StaleCategoryTree :
        PublishingUseCaseException("The categories were changed elsewhere; reload and edit again")

    /** 01-rules, 처음 정하는 값: a body shows at most 20 images. */
    class TooManyImages :
        PublishingUseCaseException("A post shows at most 20 images")
}
