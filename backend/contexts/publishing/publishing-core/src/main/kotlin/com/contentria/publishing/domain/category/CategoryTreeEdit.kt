package com.contentria.publishing.domain.category

/**
 * The whole tree as the editor submits it, in display order (01-rules 흐름 2).
 *
 * A node's [Node.key] is an existing category's id, or any temporary key the editor chose for a
 * new one; [Node.parentKey] names another node's key. Categories of the tree that are not listed
 * are removed.
 */
data class CategoryTreeEdit(val nodes: List<Node>) {

    data class Node(val key: String, val name: String, val parentKey: String? = null)
}
