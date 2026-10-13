package io.github.isouplay.roundbook

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform