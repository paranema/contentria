package com.contentria.alpha.api

import org.springframework.http.HttpStatus
import java.util.UUID

data class AlphaView(val id: UUID)

class ApiUsingSpring(val status: HttpStatus)
