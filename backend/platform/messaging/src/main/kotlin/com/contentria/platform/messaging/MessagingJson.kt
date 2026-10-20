package com.contentria.platform.messaging

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * The one JSON mapper for events. Instants as ISO-8601 text, Jackson 3's default. Unknown fields
 * are ignored, so a producer may add a field without a new version (03-context-integration, 메시지 형식).
 */
internal object MessagingJson {
    val mapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
}
