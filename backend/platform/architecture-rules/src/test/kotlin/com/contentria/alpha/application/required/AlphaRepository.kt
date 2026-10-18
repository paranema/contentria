package com.contentria.alpha.application.required

import com.contentria.alpha.domain.CleanEntity
import org.springframework.data.repository.Repository
import java.util.UUID

interface AlphaRepository : Repository<CleanEntity, UUID> {
    fun findById(id: UUID): CleanEntity?
}
