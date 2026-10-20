package com.contentria.gamma.presentation.web

import com.contentria.gamma.application.GammaService
import com.contentria.gamma.infra.client.GammaClient

class GammaController(val service: GammaService)

/** Breaks the direction rule: presentation skipping application to use infra. */
class ControllerUsingInfra(val client: GammaClient)
