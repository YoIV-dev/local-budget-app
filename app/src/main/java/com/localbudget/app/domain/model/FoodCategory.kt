package com.localbudget.app.domain.model

enum class FoodCategory(
    val displayName: String,
    val iconEmoji: String,
    val iconName: String,
    val colorHex: String,
    val colorHexLong: Long
) {
    VEGETABLE("野菜", "🥦", "Eco", "#4CAF50", 0xFF4CAF50),
    MEAT("肉", "🥩", "Restaurant", "#E53935", 0xFFE53935),
    FISH("魚", "🐟", "SetMeal", "#1E88E5", 0xFF1E88E5),
    DAIRY("乳製品", "🥛", "LocalDrink", "#FB8C00", 0xFFFB8C00),
    OTHER("その他", "🥫", "Kitchen", "#8E24AA", 0xFF8E24AA);

    companion object {
        fun fromString(value: String): FoodCategory {
            return try {
                valueOf(value)
            } catch (_: Exception) {
                OTHER
            }
        }
    }
}
