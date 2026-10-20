package com.contentria.app.api

import com.contentria.member.config.MemberCoreConfiguration
import com.contentria.member.config.MemberWebConfiguration
import com.contentria.publishing.config.PublishingCoreConfiguration
import com.contentria.publishing.config.PublishingWebConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Clock

@SpringBootApplication
@Import(
    MemberCoreConfiguration::class,
    MemberWebConfiguration::class,
    PublishingCoreConfiguration::class,
    PublishingWebConfiguration::class,
)
class ApiApplication {

    /** One clock for every context this process hosts. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
