package com.contentria.notification.application

import com.contentria.shared.exception.DomainException

sealed class NotificationException(message: String) : DomainException(message) {

    /** 01-rules 흐름 3의 3번: someone else's notification answers as one that does not exist. */
    class NotFound :
        NotificationException("No such notification")

    class InvalidCursor :
        NotificationException("Not a page cursor")
}
