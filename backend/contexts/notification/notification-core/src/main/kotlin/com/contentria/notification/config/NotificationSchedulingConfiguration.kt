package com.contentria.notification.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Removing expired notifications once a day. Loaded by the process that should run it (apps/api). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ComponentScan("com.contentria.notification.presentation.scheduling")
class NotificationSchedulingConfiguration
