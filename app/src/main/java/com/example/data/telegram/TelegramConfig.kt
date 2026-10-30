package com.example.data.telegram

object TelegramConfig {
    const val BOT_TOKEN = "9040406341:AAHjFYGQ0EcV60VpX1J7xQbVNcT29Drkl4M"
    const val SUPER_ADMIN_ID = "7334314796"

    val groups = listOf(
        TelegramGroup("حلقة الرجال - الفترة الصباحية", "https://t.me/IM_MORNING_2", "-1003701915037", "male", "morning"),
        TelegramGroup("حلقة الرجال - الفترة المسائية", "https://t.me/IF_AFTERNOON", "-1003954336712", "male", "afternoon"),
        TelegramGroup("حلقة الرجال - الفترة الليلية", "https://t.me/IM_NIGHT_1", "-1003771613278", "male", "night"),
        TelegramGroup("حلقة النساء - الفترة الصباحية", "https://t.me/IF_MORNING", "-1004464170793", "female", "morning"),
        TelegramGroup("حلقة النساء - الفترة المسائية", "https://t.me/IF_AFTERNOON", "-1004388200431", "female", "afternoon"),
        TelegramGroup("حلقة النساء - الفترة الليلية", "https://t.me/IF_NIGHT", "-1004297425702", "female", "night")
    )
}

data class TelegramGroup(
    val name: String,
    val link: String,
    val chatId: String,
    val gender: String, // "male" or "female"
    val period: String  // "morning", "afternoon", "night"
)
