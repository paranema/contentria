package com.contentria.shared.exception

/**
 * Root of every failure raised by a business rule inside a context.
 *
 * Inbound adapters translate these into their own protocol — an HTTP status in `apps/api`,
 * an exit code in `apps/batch`. The domain never knows how a failure is reported.
 */
abstract class DomainException(message: String) : RuntimeException(message)
