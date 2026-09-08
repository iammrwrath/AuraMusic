package com.metrolist.innertube.pages

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.MusicTwoRowItemRenderer
import com.metrolist.innertube.models.oddElements
import com.metrolist.innertube.models.splitBySeparator

object NewReleaseAlbumPage {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
        val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
        val playlistId =
            renderer.thumbnailOverlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint
                ?.let { ep ->
                    ep.watchPlaylistEndpoint?.playlistId ?: ep.watchEndpoint?.playlistId
                }
                ?: if (browseId.startsWith("MPREb_")) {
                    "OLAK5uy_" + browseId.removePrefix("MPREb_")
                } else {
                    browseId
                }
        val title =
            renderer.title.runs
                ?.firstOrNull()
                ?.text ?: return null
        val subtitleRuns = renderer.subtitle?.runs
        val artists = subtitleRuns?.let { runs ->
            val split = runs.splitBySeparator()
            val artistRuns = if (split.size > 1) {
                val firstToken = split[0].firstOrNull()?.text?.trim()?.lowercase()
                if (firstToken in listOf("album", "single", "ep")) {
                    split[1].oddElements()
                } else {
                    split[0].oddElements()
                }
            } else {
                runs.oddElements()
            }
            artistRuns.mapNotNull {
                it.text.takeIf { t -> t.isNotBlank() }?.let { name ->
                    Artist(
                        name = name,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }
        val year = subtitleRuns?.lastOrNull()?.text?.trim()?.toIntOrNull()

        return AlbumItem(
            browseId = browseId,
            playlistId = playlistId,
            title = title,
            artists = artists,
            year = year,
            thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
            explicit =
                renderer.subtitleBadges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
        )
    }
}
