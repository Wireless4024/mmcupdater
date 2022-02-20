import com.google.gson.Gson
import java.io.File

data class MultiMC(var components: Array<Component>, val formatVersion: Int) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is MultiMC) return false

		if (!components.contentEquals(other.components)) return false
		if (formatVersion != other.formatVersion) return false

		return true
	}

	override fun hashCode(): Int {
		var result = components.contentHashCode()
		result = 31 * result + formatVersion.hashCode()
		return result
	}

	fun updateLWJGL3(newVersion: String) {
		components.firstOrNull() { it.uid == "org.lwjgl3" }?.run {
			version = newVersion
		}
	}

	fun updateMc(newVersion: String): Boolean {
		var changed = false
		components.firstOrNull() { it.uid == "net.minecraft" }?.run {
			changed = version != newVersion
			version = newVersion
		}
		components.firstOrNull() { it.uid == "net.fabricmc.intermediary" }?.run {
			version = if (newVersion.count { it == '.' } > 2)
				newVersion.split(".").take(2).joinToString(".")
			else
				newVersion
		}
		return changed
	}

	fun updateForge(newVersion: String): Boolean {
		var changed = false

		components.firstOrNull() { it.uid == "net.minecraftforge" }?.run {
			changed = version != newVersion
			version = newVersion
		}
		return changed
	}

	fun updateFabric(newVersion: String) {
		components.firstOrNull() { it.uid == "net.minecraft" }?.run {
			version = newVersion
		}
	}

	fun saveAs(file: String) {
		File(file).outputStream().bufferedWriter().use {
			Gson().newBuilder().setPrettyPrinting().create().toJson(this, it)
		}
	}
}

data class Component(
	val uid: String,
	val important: Boolean,
	var version: String,
	val dependencyOnly: Boolean
)