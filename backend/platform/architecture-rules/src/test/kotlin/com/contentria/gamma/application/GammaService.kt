package com.contentria.gamma.application

import com.contentria.gamma.domain.GammaEntity
import com.contentria.gamma.domain.GammaRepository
import com.contentria.gamma.infra.client.GammaClient

class GammaService(val repository: GammaRepository, val client: GammaClient) {
    fun first(): GammaEntity? = null
}

/** Breaks the direction rule: application reaching up into presentation. */
class ServiceUsingController(val controller: com.contentria.gamma.presentation.web.GammaController)
