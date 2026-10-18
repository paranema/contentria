package com.contentria.app.api

import com.contentria.alpha.api.AlphaView
import com.contentria.alpha.config.AlphaConfiguration
import com.contentria.alpha.domain.CleanEntity

class AppMarker

class AppUsingDomain(val entity: CleanEntity)

class AppUsingApiAndConfiguration(val view: AlphaView, val configuration: AlphaConfiguration)

@jakarta.persistence.Entity
class AppEntity(@jakarta.persistence.Id val id: java.util.UUID)
