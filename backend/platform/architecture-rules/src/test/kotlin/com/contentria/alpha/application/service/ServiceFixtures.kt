package com.contentria.alpha.application.service

import com.contentria.alpha.adapter.required.beta.BetaAdapter
import com.contentria.alpha.application.required.AlphaRepository
import com.contentria.alpha.domain.CleanEntity
import org.springframework.data.repository.Repository
import java.util.UUID

class AlphaService(private val repository: AlphaRepository)

interface MisplacedRepository : Repository<CleanEntity, UUID>

class ServiceKnowingAdapter(private val adapter: BetaAdapter)
