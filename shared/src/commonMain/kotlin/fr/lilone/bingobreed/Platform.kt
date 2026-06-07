package fr.lilone.bingobreed

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform