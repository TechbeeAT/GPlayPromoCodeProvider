package at.techbee.gplaypromocodeprovider

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform