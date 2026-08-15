package tv.anion.source.kodik

import kotlinx.serialization.Serializable

/**
 * DTO зеркалят anion-go/internal/dto/anime.go — там уже camelCase в json-тегах,
 * конвертация ключей не нужна. Правило то же, что и в anion-dl: меняется
 * Go-структура — правится этот файл, ничего не выводим «по факту ответа».
 */
@Serializable
internal data class AnimeFeedDto(
    val seasonAnime: List<AnimeDetailsDto> = emptyList(),
    val updates: List<UpdateDto> = emptyList(),
)

@Serializable
internal data class UpdateDto(
    val animeId: Int,
    val title: String? = null,
    val animeUrl: String? = null,
    val poster: PosterDto? = null,
)

@Serializable
internal data class AnimeDetailsDto(
    val animeId: Int,
    val title: String? = null,
    val original: String? = null,
    val animeUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val poster: PosterDto? = null,
    val genres: List<GenreDto> = emptyList(),
    val episodes: EpisodesDto? = null,
    val rating: RatingDto? = null,
    val animeStatus: AnimeStatusDto? = null,
    val blockedIn: List<String> = emptyList(),
    val remoteIds: RemoteIdsDto? = null,
)

@Serializable
internal data class AnimeDto(
    val animeId: Int,
    val title: String? = null,
    val original: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val poster: PosterDto? = null,
    val genres: List<GenreDto> = emptyList(),
    val episodes: EpisodesDto? = null,
    val blockedIn: List<String> = emptyList(),
    val videos: List<VideoDto> = emptyList(),
    val remoteIds: RemoteIdsDto? = null,
    val rating: RatingDto? = null,
    val minAge: MinAgeDto? = null,
    val views: Int? = null,
    val animeStatus: AnimeStatusDto? = null,
    val type: AnimeTypeDto? = null,
    val studios: List<StudioDto> = emptyList(),
    val otherTitles: List<String> = emptyList(),
    val randomScreenshots: List<ScreenshotDto> = emptyList(),
)

@Serializable
internal data class RatingDto(val average: Double? = null, val counters: Int? = null)

@Serializable
internal data class MinAgeDto(val title: String? = null, val titleLong: String? = null)

@Serializable
internal data class AnimeStatusDto(val title: String? = null, val alias: String? = null)

@Serializable
internal data class AnimeTypeDto(val name: String? = null, val shortname: String? = null)

@Serializable
internal data class StudioDto(val title: String? = null)

@Serializable
internal data class ScreenshotDto(val sizes: ScreenshotSizesDto? = null)

@Serializable
internal data class ScreenshotSizesDto(val small: String? = null, val full: String? = null)

@Serializable
internal data class RemoteIdsDto(
    val anilibriaAlias: String? = null,
)

@Serializable
internal data class PosterDto(
    val fullsize: String? = null,
    val mega: String? = null,
    val huge: String? = null,
    val big: String? = null,
    val medium: String? = null,
    val small: String? = null,
)

@Serializable
internal data class GenreDto(val id: Int? = null, val title: String? = null)

@Serializable
internal data class EpisodesDto(val aired: Int? = null, val count: Int? = null)

@Serializable
internal data class VideoDto(
    val videoId: Int,
    val data: VideoDataDto? = null,
    val number: String? = null,
    val iframeUrl: String? = null,
    val index: Int? = null,
    val skips: VideoSkipsDto? = null,
)

@Serializable
internal data class VideoDataDto(val player: String? = null, val dubbing: String? = null)

/**
 * Только моменты начала, без длины: VideoSkipsDTO в anion-go отбрасывает
 * `Length`, который есть во внешней модели. Отсюда Segment без stop.
 */
@Serializable
internal data class VideoSkipsDto(val opening: Int? = null, val ending: Int? = null)
