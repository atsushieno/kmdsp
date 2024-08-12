package dev.atsushieno.kmdsp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform