object Diff {
	fun diff(local: Array<Mod>, server: Array<Mod>): Pair<Array<Mod>, Array<Mod>> {
		val existing = HashMap<String, Mod>()

		for (mod in local) {
			existing[mod.name] = mod
		}

		val toAdd = server.toMutableList()
		toAdd.removeAll {
			if (existing[it.name]?.version == it.version) {
				existing.remove(it.name)
				true
			} else {
				false
			}
		}

		return toAdd.toTypedArray() to existing.values.toTypedArray()
	}
}