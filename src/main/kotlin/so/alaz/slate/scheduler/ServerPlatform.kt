package so.alaz.slate.scheduler

enum class ServerPlatform {
    PAPER,
    PURPUR,
    FOLIA,
    ;

    companion object {
        val current: ServerPlatform by lazy {
            when {
                classExists("io.papermc.paper.threadedregions.RegionizedServer") -> FOLIA
                classExists("org.purpurmc.purpur.PurpurConfig") -> PURPUR
                else -> PAPER
            }
        }

        private fun classExists(name: String): Boolean = try {
            Class.forName(name)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
