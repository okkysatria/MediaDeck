package com.mediadeck.app

import com.mediadeck.app.util.media.VideoThumbnailHelper
import com.mediadeck.app.util.smb.SmbScanner
import com.mediadeck.app.util.media.MediaUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoThumbnailHelperTest {
  @Test
  fun cacheFilename_staysStableForSameMediaVersion() {
    val first = VideoThumbnailHelper.getCacheFilename(1L)
    val second = VideoThumbnailHelper.getCacheFilename(1L)

    assertEquals(first, second)
  }

  @Test
  fun cacheFilename_changesWhenMediaIdChanges() {
    val original = VideoThumbnailHelper.getCacheFilename(1L)
    val changed = VideoThumbnailHelper.getCacheFilename(2L)

    assertNotEquals(original, changed)
  }

  @Test
  fun cacheFilename_exploreVariantIsDifferentFromViewVariant() {
    val explore = VideoThumbnailHelper.getCacheFilename(1L, "explore")
    val view = VideoThumbnailHelper.getCacheFilename(1L, "view")

    assertNotEquals(explore, view)
  }

  @Test
  fun cacheFilename_defaultVariantMatchesExplore() {
    val default = VideoThumbnailHelper.getCacheFilename(1L)
    val explore = VideoThumbnailHelper.getCacheFilename(1L, "explore")

    assertEquals(default, explore)
  }

  @Test
  fun smbContentUri_roundTripsToOriginalAddress() {
    val smbUrl = "smb://server/share/Film/video.mp4"

    val contentUri = SmbScanner.toContentProviderUri(smbUrl)

        assertEquals(smbUrl, MediaUtils.getSmbUrlFromUri(contentUri))
  }
}

