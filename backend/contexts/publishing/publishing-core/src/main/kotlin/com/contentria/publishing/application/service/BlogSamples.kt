package com.contentria.publishing.application.service

import com.contentria.publishing.domain.category.CategoryTreeEdit
import com.contentria.publishing.domain.category.CategoryTreeEdit.Node

/**
 * What a new blog starts with (docs/contexts/publishing/01-rules.md, 샘플): categories anyone could
 * use, and two drafts showing what a post looks like. Drafts, so they never reach the home page.
 */
internal object BlogSamples {

    val categories = CategoryTreeEdit(
        listOf(
            Node(key = "record", name = "기록"),
            Node(key = "reading", name = "독서", parentKey = "record"),
            Node(key = "daily", name = "일상"),
        ),
    )

    val posts = listOf(
        SamplePost(title = "책 한 권을 읽고 남기는 첫 기록", categoryName = "독서", resource = "/samples/reading.md"),
        SamplePost(title = "새로운 시작, 나의 공간에 오신 것을 환영합니다", categoryName = "일상", resource = "/samples/daily.md"),
    )

    class SamplePost(val title: String, val categoryName: String, resource: String) {
        val body: String = checkNotNull(BlogSamples::class.java.getResource(resource)) { "Missing sample $resource" }.readText()
    }
}
