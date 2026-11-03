package com.localbudget.app.data.local

import com.localbudget.app.data.local.entity.CategoryEntity

object DefaultCategories {
    val list = listOf(
        CategoryEntity(
            id = 1,
            name = "食費",
            iconName = "Restaurant",
            colorHex = "#FF5722",
            isDefault = true
        ),
        CategoryEntity(
            id = 2,
            name = "日用品",
            iconName = "ShoppingCart",
            colorHex = "#4CAF50",
            isDefault = true
        ),
        CategoryEntity(
            id = 3,
            name = "交通費",
            iconName = "DirectionsSubway",
            colorHex = "#2196F3",
            isDefault = true
        ),
        CategoryEntity(
            id = 4,
            name = "住居費",
            iconName = "Home",
            colorHex = "#795548",
            isDefault = true
        ),
        CategoryEntity(
            id = 5,
            name = "水道・光熱費",
            iconName = "Bolt",
            colorHex = "#FF9800",
            isDefault = true
        ),
        CategoryEntity(
            id = 6,
            name = "通信費",
            iconName = "Wifi",
            colorHex = "#00BCD4",
            isDefault = true
        ),
        CategoryEntity(
            id = 7,
            name = "交際・娯楽費",
            iconName = "SportsEsports",
            colorHex = "#E91E63",
            isDefault = true
        ),
        CategoryEntity(
            id = 8,
            name = "医療・ヘルスケア",
            iconName = "LocalHospital",
            colorHex = "#9C27B0",
            isDefault = true
        ),
        CategoryEntity(
            id = 9,
            name = "その他",
            iconName = "MoreHoriz",
            colorHex = "#607D8B",
            isDefault = true
        )
    )
}
