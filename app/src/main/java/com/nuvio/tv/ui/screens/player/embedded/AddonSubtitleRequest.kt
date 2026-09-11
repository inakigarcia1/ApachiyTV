package com.nuvio.tv.ui.screens.player.embedded

import com.nuvio.tv.domain.model.Addon
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal object AddonSubtitleRequest {
    fun shouldPostEmbeddedReference(
        addon: Addon,
        subtitleUrl: String,
        reference: EmbeddedSubtitleReference?,
    ): Boolean = reference != null && isApachiySubtitleAddon(addon, subtitleUrl)

    fun shouldFallbackPostToGet(status: Int): Boolean = status !in 200..299

    fun buildMultipartBody(reference: EmbeddedSubtitleReference): MultipartBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val safeName = reference.filename.substringAfterLast('/').ifBlank { "embedded.srt" }
        builder.addFormDataPart(
            "reference",
            safeName,
            reference.bytes.toRequestBody("application/octet-stream".toMediaType()),
        )
        val lang = reference.language?.trim().orEmpty()
        if (lang.isNotEmpty()) {
            builder.addFormDataPart("referenceLang", lang)
        }
        return builder.build()
    }
}
