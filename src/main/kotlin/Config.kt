import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.reflect.Type

data class Config(val server: String, val port: Short) {
	suspend fun loadConfig(): Server {
		val client = HttpClient(CIO)
		val resp: HttpResponse = client.get("http://${server}:${port}/config.json")

		val server = Gson().fromJson<Server>(resp.content.readUTF8Line(Int.MAX_VALUE)!!, SERVER_TYPE)
		return server
	}

	suspend fun loadLocal(): Server? {
		val file = File("current.json")
		if (!file.exists()) return null
		return withContext(Dispatchers.IO) { file.bufferedReader().use { Gson().fromJson(it, SERVER_TYPE) } }
	}

	suspend fun saveLocal(server: Server) {
		val file = File("current.json")
		return withContext(Dispatchers.IO) { file.bufferedWriter().use { Gson().toJson(server, it) } }
	}

	suspend fun getMMC(): MultiMC {
		val file = File("../mmc-pack.json")
		if (!file.exists()) throw java.lang.RuntimeException("Not a MultiMC instance!")
		return withContext(Dispatchers.IO) { file.bufferedReader().use { Gson().fromJson(it, MMC_TYPE) } }
	}

	suspend fun updateMMC(multiMC: MultiMC) {
		val file = File("../mmc-pack.json")
		withContext(Dispatchers.IO) { file.bufferedWriter().use { Gson().toJson(multiMC, it) } }
	}

	suspend fun downloadMod(filename: String) {
		val client = HttpClient(CIO)
		val resp: HttpResponse = if (filename.startsWith("http")) client.get(filename)
		else client.get("http://${server}:${port}/mods/${filename}")

		var len = 0
		val buf = ByteArray(8192)
		val out = File("mods/${filename}").outputStream()
		out.use {
			while (resp.content.readAvailable(buf).also { len = it } > 0) {
				withContext(Dispatchers.IO) { it.write(buf, 0, len) }
			}
		}
	}

	suspend fun update() {
		withContext(Dispatchers.IO) {
			val modFolder = File("mods")
			if (!modFolder.exists()) modFolder.mkdirs()

			val _server = async { loadConfig() }
			val _local = async { loadLocal() }
			val _mmc = async { getMMC() }

			val server = _server.await()
			val local = _local.await()
			val mmc = _mmc.await()

			val jobs = mutableListOf<Deferred<Any?>>()

			if (local == null || server.config.forge_version != local.config.forge_version) {
				mmc.updateForge(server.config.forge_version)
				mmc.updateMc(server.config.mc_version)
				jobs.add(async { updateMMC(mmc) })
			}

			val (a, r) = local?.diff(server) ?: server.diff(local)

			for (mod in a.asSequence()) {
				jobs.add(async { downloadMod(mod.file_name) })
			}
			for (mod in r.asSequence()) {
				jobs.add(async { File("mods", mod.file_name).delete() })
			}
			jobs.joinAll()
			saveLocal(server)
		}
	}

	companion object {
		val SERVER_TYPE: Type = object : com.google.gson.reflect.TypeToken<Server>() {}.type
		val MMC_TYPE: Type = object : com.google.gson.reflect.TypeToken<MultiMC>() {}.type
		private val CONFIG_TYPE: Type = object : com.google.gson.reflect.TypeToken<Config>() {}.type

		fun loadFile(file: String): Config {
			return File(file).bufferedReader().use { Gson().fromJson(it, CONFIG_TYPE) }
		}
	}
}