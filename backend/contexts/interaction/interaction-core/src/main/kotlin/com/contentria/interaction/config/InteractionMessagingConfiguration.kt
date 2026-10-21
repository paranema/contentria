package com.contentria.interaction.config

import com.contentria.interaction.domain.InteractionSchema
import com.contentria.interaction.presentation.messaging.InteractionInbox
import com.contentria.platform.messaging.Inbox
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** The interaction context's event consumers and their inbox. Loaded by apps/api. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.interaction.presentation.messaging")
class InteractionMessagingConfiguration {

    @Bean(InteractionInbox.BEAN)
    fun interactionInbox(jdbc: JdbcTemplate, transactions: TransactionTemplate, clock: Clock) =
        Inbox(jdbc, transactions, InteractionSchema.NAME, clock)
}
