package com.contentria.notification.config

import com.contentria.notification.domain.NotificationSchema
import com.contentria.notification.presentation.messaging.NotificationInbox
import com.contentria.platform.messaging.Inbox
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** The notification context's event consumers and their inbox. Loaded by apps/api. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.notification.presentation.messaging")
class NotificationMessagingConfiguration {

    @Bean(NotificationInbox.BEAN)
    fun notificationInbox(jdbc: JdbcTemplate, transactions: TransactionTemplate, clock: Clock) =
        Inbox(jdbc, transactions, NotificationSchema.NAME, clock)
}
