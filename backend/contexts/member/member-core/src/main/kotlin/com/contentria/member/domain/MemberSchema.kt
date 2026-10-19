package com.contentria.member.domain

/**
 * The database schema this context owns (docs/architecture/04-persistence-conventions.md).
 * Every member table lives here, and nothing outside this context reads or writes it.
 */
object MemberSchema {
    const val NAME = "member"
}
