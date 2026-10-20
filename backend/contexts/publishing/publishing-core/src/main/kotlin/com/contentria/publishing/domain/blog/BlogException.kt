package com.contentria.publishing.domain.blog

import com.contentria.shared.exception.DomainException

sealed class BlogException(message: String) : DomainException(message) {

    class InvalidSlug(value: String) :
        BlogException("A blog slug is ${Blog.SLUG_MIN_LENGTH}-${Blog.SLUG_MAX_LENGTH} lowercase letters, digits and single hyphens: $value")

    class InvalidTitle :
        BlogException("A blog title is 1-${Blog.TITLE_MAX_LENGTH} characters")

    class InvalidDescription(length: Int) :
        BlogException("A blog description is at most ${Blog.DESCRIPTION_MAX_LENGTH} characters, was $length")
}
