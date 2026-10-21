package com.contentria.publishing.adapter.provided.web

import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.domain.blog.BlogException
import com.contentria.publishing.domain.category.CategoryException
import com.contentria.publishing.domain.post.PostException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** This context's failures as HTTP responses, for its own controllers only. Messages never echo input. */
@RestControllerAdvice(basePackageClasses = [BlogController::class])
class PublishingWebExceptionHandler {

    @ExceptionHandler
    fun handle(e: PublishingUseCaseException): ResponseEntity<ErrorResponse> = when (e) {
        is PublishingUseCaseException.BloggerNotActive -> error(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "This account cannot write.")
        is PublishingUseCaseException.BlogNotFound -> error(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "No blog has this address.")
        is PublishingUseCaseException.NotBlogOwner -> error(HttpStatus.FORBIDDEN, "NOT_BLOG_OWNER", "Only the blog's owner can change it.")
        is PublishingUseCaseException.BlogSlugTaken -> error(HttpStatus.CONFLICT, "BLOG_SLUG_TAKEN", "Another blog uses this address.")
        is PublishingUseCaseException.BlogLimitReached -> error(HttpStatus.CONFLICT, "BLOG_LIMIT_REACHED", "A member has at most three blogs.")
        is PublishingUseCaseException.CategoriesInUse -> error(HttpStatus.CONFLICT, "CATEGORIES_IN_USE", "Move the posts out of the category first.")
        is PublishingUseCaseException.StaleCategoryTree -> error(HttpStatus.CONFLICT, "CATEGORY_TREE_CHANGED", "The categories changed; reload and edit again.")
        is PublishingUseCaseException.CategoryNotFound -> error(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "The blog has no such category.")
        is PublishingUseCaseException.InvalidCursor -> error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "The page cursor is not valid.")
        is PublishingUseCaseException.PostNotFound -> error(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "No such post.")
        is PublishingUseCaseException.CategoryNotInBlog -> error(HttpStatus.BAD_REQUEST, "CATEGORY_NOT_IN_BLOG", "The category belongs to another blog.")
        is PublishingUseCaseException.PostSlugTaken -> error(HttpStatus.CONFLICT, "POST_SLUG_TAKEN", "Another post of this blog uses or used this address.")
        is PublishingUseCaseException.TooManyImages -> error(HttpStatus.BAD_REQUEST, "TOO_MANY_IMAGES", "A post shows at most 20 images.")
    }

    @ExceptionHandler
    fun handle(e: BlogException): ResponseEntity<ErrorResponse> = when (e) {
        is BlogException.InvalidSlug -> error(HttpStatus.BAD_REQUEST, "INVALID_BLOG_SLUG", "Use 3-30 lowercase letters, digits and single hyphens.")
        is BlogException.ReservedSlug -> error(HttpStatus.BAD_REQUEST, "BLOG_SLUG_RESERVED", "This address is reserved.")
        is BlogException.InvalidTitle, is BlogException.InvalidDescription ->
            error(HttpStatus.BAD_REQUEST, "INVALID_BLOG_SETTINGS", "The title or description is too long or empty.")
    }

    @ExceptionHandler
    fun handle(e: CategoryException): ResponseEntity<ErrorResponse> = when (e) {
        is CategoryException.InvalidName -> error(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_NAME", "A category name is 1-30 characters.")
        is CategoryException.TooDeep -> error(HttpStatus.BAD_REQUEST, "CATEGORY_TOO_DEEP", "Categories are two levels deep.")
        is CategoryException.DuplicateName -> error(HttpStatus.BAD_REQUEST, "CATEGORY_NAME_DUPLICATE", "Two categories under one parent share a name.")
        is CategoryException.ParentRemoved -> error(HttpStatus.BAD_REQUEST, "CATEGORY_HAS_CHILDREN", "Move or remove the sub-categories first.")
        is CategoryException.UnknownParent, is CategoryException.DuplicateKey ->
            error(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_TREE", "The tree does not hold together.")
    }

    @ExceptionHandler
    fun handle(e: PostException): ResponseEntity<ErrorResponse> = when (e) {
        is PostException.InvalidTitle, is PostException.InvalidBody, is PostException.InvalidSummary ->
            error(HttpStatus.BAD_REQUEST, "INVALID_POST", "The title, body or summary is too long or empty.")
        is PostException.InvalidSlug -> error(HttpStatus.BAD_REQUEST, "INVALID_POST_SLUG", "Use lowercase letters, digits, Hangul and single hyphens.")
        is PostException.StatusTransitionNotAllowed -> error(HttpStatus.CONFLICT, "POST_STATUS_NOT_ALLOWED", "The post cannot move to that status from its current one.")
        is PostException.SlugFollowsTitleOnlyInDraft -> error(HttpStatus.CONFLICT, "POST_SLUG_FIXED", "A published post keeps its address unless you change it.")
    }

    @ExceptionHandler
    fun handle(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.")

    private fun error(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ErrorResponse(code, message))
}
