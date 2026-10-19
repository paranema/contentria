package com.contentria.member.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/** The member context's HTTP endpoints. Loaded by apps/api. */
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.contentria.member.adapter.provided.web")
class MemberWebConfiguration
