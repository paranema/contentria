package com.contentria.publishing.application.service

import com.contentria.publishing.application.required.MarkdownPlainText
import com.contentria.publishing.domain.post.PostSummary
import org.springframework.stereotype.Component

/** 01-rules 처음 정하는 값: 작성자가 요약을 쓰지 않으면 본문에서 앞 150자. */
@Component
class PostSummaries(private val plainText: MarkdownPlainText) {

    fun of(body: String, written: String?): PostSummary =
        written?.takeIf { it.isNotBlank() }?.let(PostSummary::written)
            ?: PostSummary.derived(plainText.of(body).take(PostSummary.DERIVED_LENGTH))
}
