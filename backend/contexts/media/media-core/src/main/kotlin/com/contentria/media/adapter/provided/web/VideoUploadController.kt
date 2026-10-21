package com.contentria.media.adapter.provided.web

import com.contentria.media.application.provided.PrepareVideoUploadCommand
import com.contentria.media.application.provided.PrepareVideoUploadUseCase
import com.contentria.media.application.provided.PreparedVideoUpload
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** docs/contexts/media/01-rules.md, 흐름 V1. The browser puts the original to the storage itself. */
@RestController
class VideoUploadController(private val prepare: PrepareVideoUploadUseCase) {

    @PostMapping("/media/videos/uploads")
    fun prepare(@AuthenticationPrincipal accountId: UUID, @RequestBody request: PrepareUploadRequest): ResponseEntity<PreparedVideoUpload> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(prepare.prepare(PrepareVideoUploadCommand(accountId, request.contentType, request.sizeBytes, request.fileName)))
}
