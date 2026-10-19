package com.contentria.member.adapter.provided.web

import com.contentria.member.application.provided.GetMyProfileQuery
import com.contentria.member.application.provided.MyProfile
import com.contentria.member.application.provided.RenameProfileUseCase
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The principal is the account id, put there by the host app's access-token filter. */
@RestController
@RequestMapping("/me/profile")
class MyProfileController(
    private val getMyProfile: GetMyProfileQuery,
    private val renameProfile: RenameProfileUseCase,
) {

    @GetMapping
    fun get(@AuthenticationPrincipal accountId: UUID): MyProfile = getMyProfile.get(accountId)

    @PatchMapping
    fun rename(@AuthenticationPrincipal accountId: UUID, @Valid @RequestBody request: RenameProfileRequest): MyProfile =
        renameProfile.rename(accountId, request.displayName)
}
