// Fixtures for the layered rules: a context called gamma, one class per layer and a few that break the rules.
package com.contentria.gamma.domain

import org.springframework.data.repository.Repository
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

class GammaEntity(val id: UUID)

interface GammaRepository : Repository<GammaEntity, UUID>

/** Breaks the allowed-packages rule: a web annotation in the domain. */
@RestController
class DomainUsingWebInLayered

/** Breaks the direction rule: domain reaching up into application. */
class DomainUsingApplication(val service: com.contentria.gamma.application.GammaService)
