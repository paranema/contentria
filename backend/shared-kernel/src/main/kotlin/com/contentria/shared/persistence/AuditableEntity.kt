package com.contentria.shared.persistence

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

/**
 * Creation and modification timestamps for aggregates that need an audit trail.
 *
 * Inheriting this is a decision, not a default. It answers "does anyone need to know when this
 * row changed", so an aggregate with no such requirement should not extend it.
 *
 * Timestamps are maintained by JPA lifecycle callbacks rather than Spring Data's
 * `@CreatedDate` / `@LastModifiedDate`. Domain classes are allowed to reference
 * `jakarta.persistence` but nothing from `org.springframework`, and this way the audit
 * behaviour needs no `@EnableJpaAuditing` wiring in the application modules.
 */
@MappedSuperclass
abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @PrePersist
    protected fun onPersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
