@Suppress("ArrayInDataClass")
data class Server(val config: ForgeInfo, val mods: Array<Mod>) {
	fun diff(other: Server?): Pair<Array<Mod>, Array<Mod>> {
		if (other == null) return this.mods to emptyArray()
		return Diff.diff(this.mods, other.mods)
	}
}
data class Mod(val name: String, val version: String, val file_name: String)
data class ForgeInfo(val mc_version: String, val forge_version: String)