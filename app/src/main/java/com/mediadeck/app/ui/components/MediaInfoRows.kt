package com.mediadeck.app.ui.components

import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaFormatters
import com.mediadeck.app.util.media.MediaUtils
import com.mediadeck.app.util.media.MediaFolderIdentity

@Composable
fun movieInfoRows(movie: Movie): List<Pair<String, String>> = listOf(
    t("Type", "Jenis") to "Video",
    t("Format", "Format") to MediaUtils.getFileExt(movie.uri).uppercase().ifBlank { "—" },
    t("Size", "Ukuran") to MediaFormatters.fileSize(movie.size),
    t("Duration", "Durasi") to movie.duration.takeIf { it > 0L }?.let { MediaUtils.formatDuration(it) }.orEmpty().ifBlank { "—" },
    t("Resolution", "Resolusi") to movie.resolution.ifBlank { "—" },
    t("Folder", "Folder") to MediaFolderIdentity.displayName(movie.folderUri, movie.uri, movie.folderName),
    t("Path", "Lokasi") to movie.uri,
    t("Added", "Ditambahkan") to MediaFormatters.dateTime(movie.dateAdded, withTime = false),
    t("Tags", "Tag") to movie.tags,
    t("Description", "Deskripsi") to t("No description saved", "Belum ada deskripsi"),
)

@Composable
fun galleryInfoRows(item: GalleryItem): List<Pair<String, String>> = listOf(
    t("Type", "Jenis") to if (item.mimeType.startsWith("image/", ignoreCase = true)) "Image" else "Video",
    t("Format", "Format") to MediaUtils.getFileExt(item.uri).uppercase()
        .ifBlank { item.mimeType.substringAfter('/', "").uppercase().ifBlank { "—" } },
    t("Size", "Ukuran") to MediaFormatters.fileSize(item.size),
    t("Resolution", "Resolusi") to if (item.width > 0 && item.height > 0) "${item.width} × ${item.height}" else "—",
    t("Duration", "Durasi") to item.duration.takeIf { it > 0L }?.let { MediaUtils.formatDuration(it) }.orEmpty().ifBlank { "—" },
    t("Description", "Deskripsi") to t("No description saved", "Belum ada deskripsi"),
    t("Tags", "Tag") to item.tags,
    t("Folder", "Folder") to MediaFolderIdentity.displayName(item.folderUri, item.uri, item.folderName),
    t("Path", "Lokasi") to item.uri,
    t("Added", "Ditambahkan") to MediaFormatters.dateTime(item.dateAdded, withTime = true),
)

@Composable
fun comicInfoRows(comic: Comic, sizeBytes: Long): List<Pair<String, String>> = listOf(
    t("Type", "Jenis") to "Comic",
    t("Format", "Format") to comicArchiveFormat(comic.folderUri),
    t("Size", "Ukuran") to MediaFormatters.fileSize(sizeBytes),
    t("Total pages", "Jumlah halaman") to t("${comic.totalPages} pages", "${comic.totalPages} halaman"),
    t("Added", "Ditambahkan") to MediaFormatters.dateTime(comic.dateAdded, withTime = true),
    t("Current page", "Halaman saat ini") to if (comic.totalPages > 0) "${(comic.currentPage + 1).coerceIn(1, comic.totalPages)} / ${comic.totalPages}" else "—",
    t("Description", "Deskripsi") to t("No description saved", "Belum ada deskripsi"),
    t("Tags", "Tag") to comic.tags,
    t("Folder", "Folder") to MediaFolderIdentity.displayName(comic.folderUri, comic.coverUri, comic.parentFolderName),
    t("Path", "Lokasi") to comic.folderUri,
)

private fun comicArchiveFormat(uriString: String): String {
    val lastSegment = uriString.toUri().lastPathSegment.orEmpty()
    val extension = MediaUtils.getFileExt(lastSegment)
    return extension.takeIf(MediaUtils::isArchiveExt)?.uppercase() ?: "Folder"
}
