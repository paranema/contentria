package com.contentria.publishing.domain.category

import com.contentria.publishing.domain.category.CategoryTreeEdit.Node
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class CategoryTreeTest {

    private fun tree(vararg nodes: Node) = CategoryTree.createFor(UUID.randomUUID()).apply { replaceWith(CategoryTreeEdit(nodes.toList())) }

    private fun CategoryTree.named(name: String) = categories.single { it.name == name }

    @Test
    @DisplayName("트리 전체를 받아 만들고, 상위 다음에 그 하위가 표시 순서대로 온다")
    fun `builds the tree in display order`() {
        val tree = tree(Node("a", "기술"), Node("b", "백엔드", "a"), Node("c", "일상"), Node("d", "프런트엔드", "a"))

        assertThat(tree.categories.map { it.name }).containsExactly("기술", "백엔드", "프런트엔드", "일상")
        assertThat(tree.named("백엔드").parentId).isEqualTo(tree.named("기술").id)
        assertThat(tree.named("프런트엔드").displayOrder).isEqualTo(1)
        assertThat(tree.named("일상").displayOrder).isEqualTo(1)
    }

    @Test
    @DisplayName("하위 카테고리 아래에는 카테고리를 둘 수 없다. 깊이는 2단계다")
    fun `two levels only`() {
        assertThatThrownBy { tree(Node("a", "기술"), Node("b", "백엔드", "a"), Node("c", "코틀린", "b")) }
            .isInstanceOf(CategoryException.TooDeep::class.java)
    }

    @Test
    @DisplayName("같은 부모 아래에는 같은 이름이 둘일 수 없다. 대소문자는 가리지 않는다. 다른 부모 아래라면 된다")
    fun `sibling names are unique`() {
        assertThatThrownBy { tree(Node("a", "Kotlin"), Node("b", "kotlin")) }.isInstanceOf(CategoryException.DuplicateName::class.java)

        val tree = tree(Node("a", "기술"), Node("b", "기타", "a"), Node("c", "일상"), Node("d", "기타", "c"))
        assertThat(tree.categories.filter { it.name == "기타" }).hasSize(2)
    }

    @Test
    @DisplayName("이름은 앞뒤 공백을 뺀 뒤 1~30자다")
    fun `name length`() {
        assertThatThrownBy { tree(Node("a", "  ")) }.isInstanceOf(CategoryException.InvalidName::class.java)
        assertThatThrownBy { tree(Node("a", "가".repeat(31))) }.isInstanceOf(CategoryException.InvalidName::class.java)
    }

    @Test
    @DisplayName("하위가 남은 카테고리는 지울 수 없다")
    fun `cannot remove a parent its children still use`() {
        val tree = tree(Node("a", "기술"), Node("b", "백엔드", "a"))
        val parent = tree.named("기술").id.toString()
        val child = tree.named("백엔드").id.toString()

        assertThatThrownBy { tree.replaceWith(CategoryTreeEdit(listOf(Node(child, "백엔드", parent)))) }
            .isInstanceOf(CategoryException.ParentRemoved::class.java)
        assertThat(tree.categories).hasSize(2)
    }

    @Test
    @DisplayName("목록에 없는 카테고리는 지운다. 지울 것은 바꾸기 전에 알 수 있다")
    fun `unlisted categories are removed`() {
        val tree = tree(Node("a", "기술"), Node("b", "일상"))
        val kept = tree.named("기술")
        val removed = tree.named("일상").id
        val edit = CategoryTreeEdit(listOf(Node(kept.id.toString(), "기술")))

        assertThat(tree.categoryIdsRemovedBy(edit)).containsExactly(removed)
        tree.replaceWith(edit)

        assertThat(tree.categories).containsExactly(kept)
        assertThat(tree.contains(removed)).isFalse()
    }

    @Test
    @DisplayName("하위를 다른 상위로 옮기고 순서를 바꿀 수 있다. 기존 카테고리의 식별자는 그대로다")
    fun `moves and reorders keeping ids`() {
        val tree = tree(Node("a", "기술"), Node("b", "백엔드", "a"), Node("c", "일상"))
        val (tech, backend, daily) = listOf("기술", "백엔드", "일상").map { tree.named(it).id.toString() }

        tree.replaceWith(CategoryTreeEdit(listOf(Node(daily, "일상"), Node(backend, "백엔드", daily), Node(tech, "기술"))))

        assertThat(tree.categories.map { it.name }).containsExactly("일상", "백엔드", "기술")
        assertThat(tree.named("백엔드").id.toString()).isEqualTo(backend)
        assertThat(tree.named("백엔드").parentId.toString()).isEqualTo(daily)
    }

    @Test
    @DisplayName("slug는 이름에서 만들고 트리 안에서 겹치면 접미사를 붙인다. 이름이 그대로면 slug도 그대로다")
    fun `slugs follow names`() {
        val tree = tree(Node("a", "Kotlin"), Node("b", "기타", "a"), Node("c", "일상"), Node("d", "기타", "c"))
        assertThat(tree.named("Kotlin").slug).isEqualTo("kotlin")
        assertThat(tree.categories.filter { it.name == "기타" }.map { it.slug }).containsExactly("기타", "기타-2")

        val kotlin = tree.named("Kotlin")
        val daily = tree.named("일상")
        tree.replaceWith(CategoryTreeEdit(listOf(Node(kotlin.id.toString(), "Kotlin"), Node(daily.id.toString(), "생활"))))

        assertThat(tree.named("Kotlin").slug).isEqualTo("kotlin")
        assertThat(tree.named("생활").slug).isEqualTo("생활")
    }

    @Test
    @DisplayName("거부된 편집은 트리를 바꾸지 않는다")
    fun `a rejected edit changes nothing`() {
        val tree = tree(Node("a", "기술"))
        val before = tree.categories.map { it.id to it.name }

        assertThatThrownBy { tree.replaceWith(CategoryTreeEdit(listOf(Node("x", "새 이름"), Node("y", "새 이름")))) }
            .isInstanceOf(CategoryException.DuplicateName::class.java)

        assertThat(tree.categories.map { it.id to it.name }).isEqualTo(before)
    }

    @Test
    @DisplayName("다른 블로그의 카테고리 식별자는 새 카테고리로 다룬다")
    fun `another tree's id is a new category`() {
        val other = tree(Node("a", "남의 것"))
        val foreignId = other.categories.single().id

        val tree = tree(Node(foreignId.toString(), "내 것"))

        assertThat(tree.categories.single().id).isNotEqualTo(foreignId)
    }

    @Test
    @DisplayName("상위 카테고리는 하위를 포함한 범위를 알려준다")
    fun `a parent covers its children`() {
        val tree = tree(Node("a", "기술"), Node("b", "백엔드", "a"), Node("c", "일상"))

        assertThat(tree.withChildren(tree.named("기술").id)).containsExactlyInAnyOrder(tree.named("기술").id, tree.named("백엔드").id)
        assertThat(tree.withChildren(tree.named("백엔드").id)).containsExactly(tree.named("백엔드").id)
    }

    @Test
    @DisplayName("부모 키가 편집에 없으면 거부한다")
    fun `unknown parent`() {
        assertThatThrownBy { tree(Node("a", "기술", "nowhere")) }.isInstanceOf(CategoryException.UnknownParent::class.java)
    }
}
