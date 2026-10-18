package com.contentria.app.batch

import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
class BatchApplication

fun main(args: Array<String>) {
    val context = runApplication<BatchApplication>(*args) {
        setWebApplicationType(WebApplicationType.NONE)
    }
    exitProcess(SpringApplication.exit(context))
}
