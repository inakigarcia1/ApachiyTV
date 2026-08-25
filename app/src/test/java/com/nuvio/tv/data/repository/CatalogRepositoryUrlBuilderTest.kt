package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.dto.CatalogResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class CatalogRepositoryUrlBuilderTest {
    @Test
    fun `builds catalog url on base with metadata path prefix`() = runTest {
        val capturedUrl = slot<String>()
        val api = mockk<AddonApi>()
        coEvery { api.getCatalog(capture(capturedUrl)) } returns Response.success(CatalogResponseDto(metas = emptyList()))
        val repository = CatalogRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            api = api
        )

        val result = repository.getCatalog(
            addonBaseUrl = "https://api.apachiy.test/metadata",
            addonId = "metadata",
            addonName = "Metadata",
            catalogId = "top",
            catalogName = "Top",
            type = "movie",
            skip = 0,
            skipStep = 100,
            extraArgs = emptyMap(),
            supportsSkip = false
        ).last()

        assertEquals(true, result is NetworkResult.Success)
        assertEquals(
            "https://api.apachiy.test/metadata/catalog/movie/top.json",
            capturedUrl.captured
        )
    }

    @Test
    fun `preserves base query string when building catalog url`() = runTest {
        val capturedUrl = slot<String>()
        val api = mockk<AddonApi>()
        coEvery { api.getCatalog(capture(capturedUrl)) } returns Response.success(CatalogResponseDto(metas = emptyList()))
        val repository = CatalogRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            api = api
        )

        repository.getCatalog(
            addonBaseUrl = "https://api.apachiy.test/metadata?token=abc",
            addonId = "metadata",
            addonName = "Metadata",
            catalogId = "top",
            catalogName = "Top",
            type = "movie",
            skip = 0,
            skipStep = 100,
            extraArgs = emptyMap(),
            supportsSkip = false
        ).last()

        assertEquals(
            "https://api.apachiy.test/metadata/catalog/movie/top.json?token=abc",
            capturedUrl.captured
        )
    }
}
