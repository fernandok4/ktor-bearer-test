package br.com.kanasha

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.delay
import java.util.*
import kotlin.random.Random

class ServerApplication

fun main() {

    var token = SimpleToken(UUID.randomUUID().toString(), UUID.randomUUID().toString())
    var tokenStart = System.currentTimeMillis()

    embeddedServer(Netty, port = 8080) {
        install(CallLogging)
        install(ContentNegotiation)
        routing {
            get("/hello-world") {
                try{
                    if(System.currentTimeMillis() - tokenStart > token.expirationTime){
                        call.response.status(HttpStatusCode.Unauthorized)
                        return@get
                    }
                    if("Bearer ${token.accessToken}" == call.request.header("Authorization")){
                        call.response.status(HttpStatusCode.Unauthorized)
                        return@get
                    }
                    val nextLong = Random.Default.nextLong(0, 1500)
                    delay(1000 + nextLong)
                    call.respondText("Hello, world!")
                }catch (e: Exception){
                    println("Something is Wrong $e")
                    call.respondText("Something is Wrong")
                }
            }

            get("/token") {
                delay(50 + Random.Default.nextLong(0, 1000))
                tokenStart = System.currentTimeMillis()
                token = SimpleToken(UUID.randomUUID().toString(), UUID.randomUUID().toString())
                call.respond(token)
            }
        }
    }.start(wait = true)
}

data class SimpleToken(
    var accessToken: String,
    var refreshToken: String,
    var expirationTime: Long = 30000
)