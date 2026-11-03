package at.techbee.gplaypromocodeprovider.invite

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry holding [text]. Created differently per platform (browser clipboard API, AWT on the JVM). */
expect fun plainTextClipEntry(text: String): ClipEntry
