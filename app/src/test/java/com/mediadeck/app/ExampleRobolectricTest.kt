package com.mediadeck.app

import org.junit.Assert.assertEquals
import com.mediadeck.app.util.media.MediaUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaUtilsTest {

  @Test
  fun `format duration for movie card`() {
    assertEquals("01:05", MediaUtils.formatDuration(65_000L))
    assertEquals("01:01:01", MediaUtils.formatDuration(3_661_000L))
  }
}
