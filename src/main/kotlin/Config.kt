import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.File
import java.lang.reflect.Type
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ConfigSync(
	val on_launch: Boolean?,
	val on_mc_update: Boolean?,
	val on_forge_update: Boolean?,
	val on_mod_update: Boolean?
)

data class Config(val server: String, val port: Short, val config_sync: ConfigSync) {
	suspend fun loadConfig(): Server {
		val client = HttpClient(CIO)
		val resp: HttpResponse = client.get("http://${server}:${port}/config.json")

		val server = Gson().fromJson<Server>(resp.content.readUTF8Line(Int.MAX_VALUE)!!, SERVER_TYPE)
		return server
	}

	suspend fun loadConfigFile(): ZipInputStream {
		val client = HttpClient(CIO)
		val resp: HttpResponse = client.get("http://${server}:${port}/config.zip")

		return ZipInputStream(resp.content.toInputStream())
	}

	private var alreadySyncConfig: Boolean = false


	suspend fun syncConfigs() {
		if (alreadySyncConfig) return
		alreadySyncConfig = true
		try {
			val cfgFolder = File("config")

			if (!cfgFolder.exists()) cfgFolder.mkdir()

			val configData = loadConfigFile()

			val cfgBackUpFolder = File("config.old")
			if (!cfgBackUpFolder.exists()) cfgBackUpFolder.mkdir()


			configData.use {
				var ent: ZipEntry?

				while (it.nextEntry.also { e -> ent = e } != null) {
					val e = ent ?: break
					if (!e.isDirectory) {
						val relativeName = e.name.drop(7)

						val outFile = File(cfgFolder, relativeName)
						if (outFile.exists()) {
							File(cfgBackUpFolder, relativeName).also { out ->
								out.parentFile.mkdirs()
								outFile.renameTo(out)
							}
						}
						outFile.parentFile.mkdirs()
						outFile.outputStream().run {
							it.transferTo(this)
							it.closeEntry()
						}
					}
				}
			}
		} catch (_: Throwable) {
			System.err.println("Server doesn't provide config file")
		}
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

			val jobs = mutableListOf<Deferred<Any?>>()

			if (config_sync.on_launch == true) {
				jobs.add(async { syncConfigs() })
			}

			val server = _server.await()
			val local = _local.await()
			val mmc = _mmc.await()

			val modBackUpFolder = File("mods.old")
			if (!modBackUpFolder.exists()) modBackUpFolder.mkdir()
			if (local == null) {
				File("mods").listFiles().asSequence().filter { it.isFile }.map {
					println(File(modBackUpFolder, it.name))
					async { it.renameTo(File(modBackUpFolder, it.name)) }
				}.forEach { it.join() }
			}

			if (local == null || server.config.forge_version != local.config.forge_version) {
				if (mmc.updateForge(server.config.forge_version) && config_sync.on_forge_update == true && !alreadySyncConfig) {
					jobs.add(async { syncConfigs() })
				}
				if (mmc.updateMc(server.config.mc_version) && config_sync.on_mc_update != false && !alreadySyncConfig) {
					jobs.add(async { syncConfigs() })
				}
				jobs.add(async { updateMMC(mmc) })
			}

			val (a, r) = local?.diff(server) ?: server.diff(local)

			if ((a.isNotEmpty() || r.isNotEmpty()) && config_sync.on_mc_update != false && !alreadySyncConfig) {
				jobs.add(async { syncConfigs() })
			}

			for (mod in a) {
				jobs.add(async { downloadMod(mod.file_name) })
			}

			for (mod in r) {
				jobs.add(async {
					File("mods", mod.file_name).renameTo(File(modBackUpFolder, mod.file_name))
				})
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