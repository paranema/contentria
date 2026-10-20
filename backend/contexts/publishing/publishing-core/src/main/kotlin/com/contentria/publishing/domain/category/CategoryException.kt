package com.contentria.publishing.domain.category

import com.contentria.shared.exception.DomainException

sealed class CategoryException(message: String) : DomainException(message) {

    class InvalidName :
        CategoryException("A category name is 1-${Category.NAME_MAX_LENGTH} characters")

    /** 01-rules 흐름 2의 2번 */
    class TooDeep(key: String) :
        CategoryException("Category $key sits under a sub-category; the tree is two levels deep")

    /** 01-rules 흐름 2의 3번 */
    class DuplicateName(name: String) :
        CategoryException("Two categories under the same parent are called $name")

    /** 01-rules 흐름 2의 4번: a category is removed while a child still points at it. */
    class ParentRemoved(key: String) :
        CategoryException("Category $key stays under a category that is being removed")

    class UnknownParent(key: String) :
        CategoryException("Category $key names a parent that is not in the tree")

    class DuplicateKey(key: String) :
        CategoryException("The edit lists $key twice")
}
