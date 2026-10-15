package com.nuvio.tv.data.remote.device.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchSuggestionsResponse(
    @Json(name = "suggestions") val suggestions: List<SearchSuggestionDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SearchSuggestionDto(
    @Json(name = "title") val title: String = "",
    @Json(name = "year") val year: String? = null,
    @Json(name = "type") val type: String = ""
)
