package com.contentria.interaction.infra.client

import com.contentria.interaction.domain.target.TargetBlog
import com.contentria.interaction.domain.target.TargetPost
import com.contentria.member.api.AccountStatusView
import com.contentria.member.api.GetAccountStatusQuery
import com.contentria.publishing.api.BlogReference
import com.contentria.publishing.api.GetBlogReferencesQuery
import com.contentria.publishing.api.GetPostReferencesQuery
import com.contentria.publishing.api.PostReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class ClientsTest {

    private val blog = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val published = UUID.randomUUID()
    private val draft = UUID.randomUUID()

    private val publishing = PublishingClient(
        object : GetPostReferencesQuery {
            private val known = mapOf(
                published to PostReference(published, blog, owner, isPublic = true),
                draft to PostReference(draft, blog, owner, isPublic = false),
            )
            override fun getPosts(postIds: Set<UUID>) = known.filterKeys { it in postIds }
        },
        object : GetBlogReferencesQuery {
            override fun getBlogBySlug(slug: String) = if (slug == "my-blog") BlogReference(blog, slug, owner) else null
            override fun getBlogs(blogIds: Set<UUID>) = emptyMap<UUID, BlogReference>()
        },
    )

    @Test
    @DisplayName("발행된 글만 상호작용의 대상이 된다. 임시저장이나 비공개, 모르는 글은 없는 것과 같다")
    fun `only published posts are targets`() {
        assertThat(publishing.publicPost(published)).isEqualTo(TargetPost(published, blog, owner))
        assertThat(publishing.publicPost(draft)).isNull()
        assertThat(publishing.publicPost(UUID.randomUUID())).isNull()
        assertThat(publishing.publicPosts(setOf(published, draft)).keys).containsExactly(published)
    }

    @Test
    @DisplayName("블로그는 slug로 찾아 상호작용의 타입으로 바꾼다")
    fun `blogs by slug`() {
        assertThat(publishing.blogBySlug("my-blog")).isEqualTo(TargetBlog(blog, owner, "my-blog"))
        assertThat(publishing.blogBySlug("nowhere")).isNull()
    }

    @Test
    @DisplayName("활성 계정만 상호작용한다. 회원이 모르는 계정도 할 수 없다")
    fun `only active accounts interact`() {
        val active = UUID.randomUUID()
        val suspended = UUID.randomUUID()
        val member = MemberClient(object : GetAccountStatusQuery {
            private val known = mapOf(
                active to AccountStatusView(active, AccountStatusView.Status.ACTIVE),
                suspended to AccountStatusView(suspended, AccountStatusView.Status.SUSPENDED),
            )
            override fun get(accountId: UUID) = known[accountId]
            override fun getAll(accountIds: Set<UUID>) = known.filterKeys { it in accountIds }
        })

        assertThat(member.canInteract(active)).isTrue()
        assertThat(member.canInteract(suspended)).isFalse()
        assertThat(member.canInteract(UUID.randomUUID())).isFalse()
    }
}
