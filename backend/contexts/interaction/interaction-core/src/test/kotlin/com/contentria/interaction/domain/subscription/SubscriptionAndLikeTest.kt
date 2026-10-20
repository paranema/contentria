package com.contentria.interaction.domain.subscription

import com.contentria.interaction.domain.like.Like
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

class SubscriptionAndLikeTest {

    @Test
    @DisplayName("구독은 회원과 블로그를, 좋아요는 회원과 글과 블로그를 ID로 담는다")
    fun `they hold ids only`() {
        val member = UUID.randomUUID()
        val blog = UUID.randomUUID()
        val post = UUID.randomUUID()

        val subscription = Subscription.of(member, blog)
        val like = Like.of(member, post, blog)

        assertThat(subscription.subscriberId to subscription.blogId).isEqualTo(member to blog)
        assertThat(Triple(like.accountId, like.postId, like.blogId)).isEqualTo(Triple(member, post, blog))
        assertThat(Subscription.of(member, blog).id).isNotEqualTo(subscription.id)
    }
}
