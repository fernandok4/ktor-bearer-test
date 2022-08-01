package br.com.kanasha

import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.*
import java.time.Duration
import java.time.temporal.ChronoUnit

class ClientRequestApplication

@OptIn(DelicateCoroutinesApi::class)
suspend fun main() {
    repeat(100000){ count ->
        println("Request Count $count")
        GlobalScope.launch { getHelloWorld(count) }
    }
    delay(6000000)
}

private suspend fun getHelloWorld(count: Int): String {
    val helloWorld: String = SemaphoreBulkheadUtils.executeSuspending {
        println("Request $count started!!")
        val responseText = try {
            client.get("http://localhost:8080/hello-world").bodyAsText()
        }catch (e: Exception) {
            "Something is wrong!"
        }
        println("Chamada $count: $responseText")
        responseText
    }
    return helloWorld
}

private val client = HttpClient(CIO) {
    expectSuccess = true
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
    HttpResponseValidator {
        validateResponse { response ->
            if(response.status == HttpStatusCode.NoContent){
                throw ClientRequestException(response, "Sem conteudo no retorno")
            }
        }
    }
    engine {
        threadsCount = 200
        pipelining = true
        maxConnectionsCount = 1000
        endpoint {
            maxConnectionsPerRoute = 100
            pipelineMaxSize = 600
            keepAliveTime = HttpTimeout.INFINITE_TIMEOUT_MS
            connectTimeout = 60000
            connectAttempts = 5
        }
    }
    install(ContentNegotiation){
        jackson()
    }
    defaultRequest {
        accept(ContentType.Application.Json)
    }
    install(HttpRequestRetry) {
        retryOnException(maxRetries = 5)
        constantDelay(10000)
    }
    install(Auth) {
        bearer {
            loadTokens {
                println("Load token")
                BearerTokens("test", "test")
            }
            refreshTokens {
                println("Refresh token")
                val response = authClient.get("http://localhost:8080/token"){
                    markAsRefreshTokenRequest()
                }.body<SimpleTokenResponse>()
                BearerTokens(response.accessToken, response.refreshToken)
            }
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60000
        socketTimeoutMillis = 60000
        connectTimeoutMillis = 60000
    }
}

private val authClient = HttpClient(CIO) {
    expectSuccess = true
}

data class SimpleTokenResponse(
    var accessToken: String = "",
    var refreshToken: String = ""
)

object SemaphoreBulkheadUtils {

    private val config = BulkheadConfig.custom()
        .maxConcurrentCalls(1)
        .maxWaitDuration(Duration.of(24, ChronoUnit.HOURS))
        .build()
    private val unique = BulkheadRegistry.of(config).bulkhead("DEFAULT")

    suspend fun <T> executeSuspending(block: suspend () -> T): T{
        return unique.executeSuspendFunction { block.invoke() }
    }
}