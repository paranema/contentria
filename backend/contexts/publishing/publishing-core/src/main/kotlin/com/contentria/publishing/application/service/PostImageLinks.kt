package com.contentria.publishing.application.service

import com.contentria.publishing.application.provided.PublishingUseCaseException
import com.contentria.publishing.application.required.MarkdownImages
import com.contentria.publishing.application.required.PostImages
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The post's side of its images (docs/architecture/03-context-integration.md, 글 저장과 이미지 연결).
 * The count is checked before the post is saved. Media is told after, and a failure there does not
 * undo the save: the next save puts it right, or media's cleanup takes what stays unattached.
 */
@Component
class PostImageLinks(
    private val markdown: MarkdownImages,
    private val images: PostImages,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 01-rules, 처음 정하는 값: every image the body shows counts, other sites' included. */
    fun requireWithinLimit(body: String) {
        if (markdown.urlsIn(body).size > MAX_IMAGES) throw PublishingUseCaseException.TooManyImages()
    }

    /** The images of [body] and the attached [videoId], each told on its own: one failing leaves the other done. */
    fun afterSave(postId: UUID, ownerId: UUID, body: String, videoId: UUID? = null) {
        quietly(postId) { images.sync(postId, ownerId, markdown.urlsIn(body)) }
        quietly(postId) { images.syncVideo(postId, ownerId, videoId) }
    }

    fun afterDelete(postId: UUID) = quietly(postId) { images.release(postId) }

    private fun quietly(postId: UUID, call: () -> Unit) {
        try {
            call()
        } catch (e: RuntimeException) {
            log.warn("Images of post {} not brought in line with media; the next save does it", postId, e)
        }
    }

    companion object {
        const val MAX_IMAGES = 20
    }
}
