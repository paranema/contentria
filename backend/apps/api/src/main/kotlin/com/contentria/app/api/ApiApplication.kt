package com.contentria.app.api

import com.contentria.analytics.config.AnalyticsCoreConfiguration
import com.contentria.analytics.config.AnalyticsWebConfiguration
import com.contentria.interaction.config.InteractionCoreConfiguration
import com.contentria.interaction.config.InteractionMessagingConfiguration
import com.contentria.interaction.config.InteractionWebConfiguration
import com.contentria.media.config.MediaCoreConfiguration
import com.contentria.media.config.MediaWebConfiguration
import com.contentria.member.config.MemberCoreConfiguration
import com.contentria.member.config.MemberWebConfiguration
import com.contentria.notification.config.NotificationCoreConfiguration
import com.contentria.notification.config.NotificationMessagingConfiguration
import com.contentria.notification.config.NotificationSchedulingConfiguration
import com.contentria.notification.config.NotificationWebConfiguration
import com.contentria.publishing.config.PublishingCoreConfiguration
import com.contentria.platform.messaging.MessagingConfiguration
import com.contentria.publishing.config.PublishingWebConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Duration

@SpringBootApplication
@Import(
    MemberCoreConfiguration::class,
    MemberWebConfiguration::class,
    PublishingCoreConfiguration::class,
    PublishingWebConfiguration::class,
    InteractionCoreConfiguration::class,
    InteractionWebConfiguration::class,
    InteractionMessagingConfiguration::class,
    NotificationCoreConfiguration::class,
    NotificationMessagingConfiguration::class,
    NotificationWebConfiguration::class,
    NotificationSchedulingConfiguration::class,
    MediaCoreConfiguration::class,
    MediaWebConfiguration::class,
    AnalyticsCoreConfiguration::class,
    AnalyticsWebConfiguration::class,
    MessagingConfiguration::class,
)
class ApiApplication {

    /**
     * One clock for every context this process hosts. It ticks in microseconds, PostgreSQL's
     * timestamp precision: Linux JDKs give nanoseconds, so a time returned before saving would
     * differ from the same time read back.
     */
    @Bean
    fun clock(): Clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000))
}

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
