package me.telosmancy.profile

import com.google.gson.*
import me.telosmancy.Telosmancy
import me.telosmancy.clickgui.settings.Saving
import me.telosmancy.features.ModuleManager
import java.io.File

/**
 * Saves and loads named configuration profiles.
 * Each profile is a full snapshot of all module enabled-states and settings,
 * stored as a JSON file under config/telosmancy/profiles/.
 */
object ProfileManager {

    val profilesDir: File by lazy {
        File(Telosmancy.configFile, "profiles").also { it.mkdirs() }
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun list(): List<String> =
        profilesDir.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun save(name: String) {
        val jsonArray = JsonArray()
        for ((_, module) in ModuleManager.modules) {
            jsonArray.add(JsonObject().apply {
                add("name", JsonPrimitive(module.name))
                add("enabled", JsonPrimitive(module.enabled))
                add("settings", JsonObject().apply {
                    for ((k, s) in module.settings) {
                        if (s is Saving) add(k, s.write(gson))
                    }
                })
            })
        }
        File(profilesDir, "${sanitize(name)}.json").bufferedWriter().use { it.write(gson.toJson(jsonArray)) }
    }

    fun load(name: String): Boolean {
        val file = File(profilesDir, "${sanitize(name)}.json")
        if (!file.exists()) return false
        return try {
            val jsonArray = JsonParser.parseString(file.readText()).asJsonArray ?: return false
            for (el in jsonArray) {
                val obj = el.asJsonObject
                val module = ModuleManager.modules[obj.get("name").asString.lowercase()] ?: continue
                val shouldBeEnabled = obj.get("enabled").asBoolean
                if (shouldBeEnabled != module.enabled) module.toggle()
                val settingsObj = obj.get("settings")
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.entrySet() ?: continue
                for ((k, v) in settingsObj) {
                    (module.settings[k] as? Saving)?.read(v, gson)
                }
            }
            ModuleManager.saveConfigurations()
            true
        } catch (e: Exception) {
            Telosmancy.logger.error("Failed to load profile '$name'", e)
            false
        }
    }

    fun delete(name: String): Boolean =
        File(profilesDir, "${sanitize(name)}.json").let { if (it.exists()) it.delete() else false }

    // Strip characters that are unsafe in filenames; truncate to 64 chars
    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_").take(64)
}
