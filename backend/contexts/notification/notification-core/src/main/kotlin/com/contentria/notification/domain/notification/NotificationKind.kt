package com.contentria.notification.domain.notification

/** docs/contexts/notification/01-rules.md, 알림 종류. */
enum class NotificationKind {

    /** A blog the recipient subscribes to published a post. */
    NEW_POST,

    /** Someone commented or replied on the recipient's post. */
    COMMENT_ON_POST,

    /** Someone replied to the recipient's comment. */
    REPLY_TO_COMMENT,
}
