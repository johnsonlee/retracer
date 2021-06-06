package io.johnsonlee.retracer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Retracer

fun main(args: Array<String>) {
    runApplication<Retracer>(*args)
}
