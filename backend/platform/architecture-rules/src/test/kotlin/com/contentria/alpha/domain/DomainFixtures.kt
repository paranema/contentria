package com.contentria.alpha.domain

import com.contentria.app.api.AppMarker
import com.contentria.beta.api.BetaQuery
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import java.util.UUID

// Fixtures for the architecture rules. Each class either follows or breaks exactly one rule.

@Entity
class CleanEntity(@Id val id: UUID, val count: Int, val tags: List<String>)

class DomainUsingWeb(val status: HttpStatus)

class DomainUsingOtherContextApi(val query: BetaQuery)

class DomainUsingPageable(val pageable: Pageable)

class DomainUsingAppPackage(val marker: AppMarker)
