package com.koma.client.data.server.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class KavitaSeriesFilterDto(
    val statements: List<KavitaFilterStatement> = emptyList(),
    val combination: Int = 1, // 1 = AND
    val limitTo: Int = 0, // 0 = no limit
    val sortOptions: KavitaSortOptions = KavitaSortOptions(),
)

@Serializable
data class KavitaFilterStatement(
    val comparison: Int = 0,
    val field: Int = 0,
    val value: String = "",
)

@Serializable
data class KavitaSortOptions(
    val sortField: Int = 1, // 1 = sort by name
    val isAscending: Boolean = true,
)
