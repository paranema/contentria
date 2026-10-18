package com.contentria.member.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration

/**
 * Wires the member context into whichever application module hosts it.
 *
 * Every application module has its own component scan root, so nothing under
 * `com.contentria.member` is found by default. Declaring the scan here rather than on each
 * application class keeps knowledge of this context's package layout inside the context, and
 * keeps the JPA dependency off the application modules' compile classpath: they only need
 * `@Import(MemberCoreConfiguration::class)`.
 */
@Configuration(proxyBeanMethods = false)
@EntityScan("com.contentria.member")
class MemberCoreConfiguration
