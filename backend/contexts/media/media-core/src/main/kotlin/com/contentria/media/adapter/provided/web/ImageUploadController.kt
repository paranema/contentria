package com.contentria.media.adapter.provided.web

import com.contentria.media.application.provided.CompleteImageUploadUseCase
import com.contentria.media.application.provided.DeleteImageUseCase
import com.contentria.media.application.provided.ImageView
import com.contentria.media.application.provided.PrepareImageUploadCommand
import com.contentria.media.application.provided.PrepareImageUploadUseCase
import com.contentria.media.application.provided.PreparedImageUpload
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * docs/contexts/media/01-rules.md, 흐름 1, 2, 5. The file itself never passes through here: the
 * browser puts it to the storage with the upload address.
 */
@RestController
class ImageUploadController(
    private val prepare: PrepareImageUploadUseCase,
    private val complete: CompleteImageUploadUseCase,
    private val delete: DeleteImageUseCase,
) {

    @PostMapping("/media/uploads")
    fun prepare(@AuthenticationPrincipal accountId: UUID, @RequestBody request: PrepareUploadRequest): ResponseEntity<PreparedImageUpload> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(prepare.prepare(PrepareImageUploadCommand(accountId, request.contentType, request.sizeBytes, request.fileName)))

    @PostMapping("/media/{id}/complete")
    fun complete(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ImageView = complete.complete(accountId, id)

    @DeleteMapping("/media/{id}")
    fun delete(@AuthenticationPrincipal accountId: UUID, @PathVariable id: UUID): ResponseEntity<Void> {
        delete.delete(accountId, id)
        return ResponseEntity.noContent().build()
    }
}

data class PrepareUploadRequest(val contentType: String, val sizeBytes: Long, val fileName: String)
