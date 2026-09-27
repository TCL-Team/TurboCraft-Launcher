package com.movtery.zalithlauncher.utils.settings

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.path.PathManager
import java.io.File

class MobileGluesConfig private constructor(private var isInitializing: Boolean) {

    constructor() : this(false)

    private val _settings = mutableMapOf<String, Int>()

    val allKeys: Set<String> get() = _settings.keys

    fun get(key: String, default: Int = 0): Int = _settings[key] ?: default
    fun set(key: String, value: Int) {
        if (_settings[key] != value) {
            _settings[key] = value
            saveIfReady()
        }
    }

    fun setAll(map: Map<String, Int>) {
        _settings.putAll(map)
    }

    private fun saveIfReady() {
        if (!isInitializing) save()
    }

    fun save() {
        runCatching {
            /* Native MobileGlues reads ignoreError from /sdcard/MG/config.json.
               The settings UI historically wrote enableNoError under DIR_GAME/MG. */
            if (!_settings.containsKey("ignoreError")) {
                val noError = _settings["enableNoError"] ?: 0
                _settings["ignoreError"] = if (noError > 0) 1 else 0
            }
            val json = Gson().toJson(_settings)
            for (path in listOf(CONFIG_FILE_PATH, SDCARD_CONFIG_PATH)) {
                val configFile = File(path)
                configFile.parentFile?.mkdirs()
                configFile.writeText(json)
            }
        }
    }

    companion object {
        private const val CONFIG_SUBDIR = "MG"
        private const val CONFIG_FILE = "config.json"

        val CONFIG_FILE_PATH: String
            get() = "${PathManager.DIR_GAME.absolutePath}/$CONFIG_SUBDIR/$CONFIG_FILE"

        const val SDCARD_CONFIG_PATH: String = "/sdcard/MG/$CONFIG_FILE"

        fun load(): MobileGluesConfig? {
            val configFile = File(CONFIG_FILE_PATH)
            if (!configFile.exists()) return null

            val configStr = runCatching { configFile.readText() }.getOrNull() ?: return null

            return runCatching {
                val obj: JsonObject = JsonParser.parseString(configStr).asJsonObject
                val config = MobileGluesConfig(isInitializing = true)
                config.applyFromJson(obj)
                config.isInitializing = false
                config
            }.getOrNull()
        }

        private fun MobileGluesConfig.applyFromJson(obj: JsonObject) {
            for (key in obj.keySet()) {
                _settings[key] = obj.get(key).asInt
            }
        }
    }
}
