package br.com.kanasha

import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.temporal.ChronoUnit

class BulkheadTest

suspend fun main() {
    repeat(10000){
        executeUsingB3ISemaphoreBulkhead {
            println("Começou: $it")
            delay(1000)
            println("Terminou: $it")
        }
    }
}

private val config = BulkheadConfig.custom()
    .maxConcurrentCalls(1)
    .maxWaitDuration(Duration.of(24, ChronoUnit.HOURS))
    .build()

suspend fun <T> executeUsingB3ISemaphoreBulkhead(block: suspend () -> T): T{
    return BulkheadRegistry.of(config).bulkhead("DEFAULT").executeSuspendFunction { block.invoke() }
}