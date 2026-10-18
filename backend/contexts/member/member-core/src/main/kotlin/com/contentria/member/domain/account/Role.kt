package com.contentria.member.domain.account

/**
 * Authorities granted to an account.
 *
 * Modelled as a value, not an entity: the set is fixed, closed, and carries no state of its
 * own. The `ROLE_` prefix Spring Security expects is a framework naming convention and is
 * added by the security adapter in `apps/api`, never stored here.
 */
enum class Role {
    USER,
    ADMIN,
}
