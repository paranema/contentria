package com.contentria.member.domain.profile

import com.contentria.shared.exception.DomainException

sealed class ProfileException(message: String) : DomainException(message) {

    class InvalidDisplayName(value: String) : ProfileException(
        "A display name must be ${Profile.DISPLAY_NAME_MIN_LENGTH}-" +
            "${Profile.DISPLAY_NAME_MAX_LENGTH} characters: $value",
    )

    class InvalidPictureUrl(length: Int) : ProfileException(
        "A picture URL may be at most ${Profile.PICTURE_URL_MAX_LENGTH} characters, was $length",
    )
}
