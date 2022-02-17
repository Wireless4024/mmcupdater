suspend fun main(args: Array<String>) {
	val config = Config.loadFile(args.firstOrNull() ?: "config.json")
	config.update()
}