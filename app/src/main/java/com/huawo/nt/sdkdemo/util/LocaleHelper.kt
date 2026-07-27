package com.huawo.nt.sdkdemo.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val prefValue: String) {
    SYSTEM("system"),
    CHINESE("zh"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromPref(value: String?): AppLanguage =
            entries.firstOrNull { it.prefValue == value } ?: SYSTEM
    }
}

object LocaleHelper {
    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE = "app_language"

    fun getLanguage(context: Context): AppLanguage {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.SYSTEM.prefValue)
        return AppLanguage.fromPref(value)
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.prefValue)
            .apply()
    }

    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        if (language == AppLanguage.SYSTEM) return context
        val locale = when (language) {
            AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.SYSTEM -> return context
        }
        return updateResources(context, locale)
    }

    fun localizedContext(context: Context): Context = wrap(context)

    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }
}
