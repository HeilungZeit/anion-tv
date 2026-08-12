package tv.anion.source.anilibria

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO ответов https://anilibria.top/api/v1. Заполнено по живым ответам, копии
 * которых лежат в src/test/resources/fixtures — тесты маппера гоняются на них,
 * а не на выдуманной форме.
 */
@Serializable
internal data class ReleaseDto(
    val id: Int,
    val alias: String? = null,
    val year: Int? = null,
    val name: NameDto? = null,
    val poster: PosterDto? = null,
    val description: String? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    val type: ReleaseTypeDto? = null,
    @SerialName("age_rating") val ageRating: AgeRatingDto? = null,
    @SerialName("is_in_production") val isInProduction: Boolean = false,
    @SerialName("is_blocked_by_geo") val isBlockedByGeo: Boolean = false,
    @SerialName("is_blocked_by_copyrights") val isBlockedByCopyrights: Boolean = false,
    val genres: List<GenreDto> = emptyList(),
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
internal data class ReleaseTypeDto(val value: String? = null, val description: String? = null)

@Serializable
internal data class AgeRatingDto(val label: String? = null)

@Serializable
internal data class NameDto(
    val main: String? = null,
    val english: String? = null,
    val alternative: String? = null,
)

/** У постера четыре размера; на плитку берётся thumbnail, на фон — src. */
@Serializable
internal data class PosterDto(
    val src: String? = null,
    val preview: String? = null,
    val thumbnail: String? = null,
    val optimized: OptimizedPosterDto? = null,
)

@Serializable
internal data class OptimizedPosterDto(
    val src: String? = null,
    val preview: String? = null,
    val thumbnail: String? = null,
)

@Serializable
internal data class GenreDto(val id: Int? = null, val name: String? = null)

@Serializable
internal data class EpisodeDto(
    val id: String? = null,
    val name: String? = null,
    val ordinal: Double? = null,
    val opening: TimeRangeDto? = null,
    val ending: TimeRangeDto? = null,
    val preview: PosterDto? = null,
    val duration: Int? = null,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
)

/** Здесь отрезок полный: и start, и stop — в отличие от anion-go. */
@Serializable
internal data class TimeRangeDto(val start: Int? = null, val stop: Int? = null)

@Serializable
internal data class CatalogDto(val data: List<ReleaseDto> = emptyList(), val meta: MetaDto? = null)

@Serializable
internal data class MetaDto(val pagination: PaginationDto? = null)

@Serializable
internal data class PaginationDto(
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
)
