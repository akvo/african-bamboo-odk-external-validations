package org.akvo.afribamodkvalidator.data

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.akvo.afribamodkvalidator.data.dto.GitHubAssetDto
import org.akvo.afribamodkvalidator.data.dto.GitHubReleaseDto
import org.akvo.afribamodkvalidator.data.network.GitHubApiService
import org.akvo.afribamodkvalidator.data.repository.AppUpdateRepository
import org.akvo.afribamodkvalidator.data.repository.AppUpdateRepository.Companion.isNewerVersion
import org.akvo.afribamodkvalidator.data.repository.UpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AppUpdateRepositoryTest {

    private lateinit var gitHubApiService: GitHubApiService
    private lateinit var context: Context
    private lateinit var repository: AppUpdateRepository

    @Before
    fun setUp() {
        gitHubApiService = mockk()
        context = RuntimeEnvironment.getApplication()
        repository = AppUpdateRepository(gitHubApiService, context)
    }

    // ── Version comparison tests ──

    @Test
    fun `newer major version is detected`() {
        assertTrue(isNewerVersion("2.0", "1.6"))
    }

    @Test
    fun `newer minor version is detected`() {
        assertTrue(isNewerVersion("1.7", "1.6"))
    }

    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewerVersion("1.6.1", "1.6"))
    }

    @Test
    fun `same version returns false`() {
        assertFalse(isNewerVersion("1.6", "1.6"))
    }

    @Test
    fun `older version returns false`() {
        assertFalse(isNewerVersion("1.5", "1.6"))
    }

    @Test
    fun `older major version returns false`() {
        assertFalse(isNewerVersion("1.9.9", "2.0"))
    }

    @Test
    fun `handles v prefix on remote`() {
        assertTrue(isNewerVersion("v1.7", "1.6"))
    }

    @Test
    fun `handles v prefix on both`() {
        assertTrue(isNewerVersion("v2.0", "v1.6"))
    }

    @Test
    fun `handles pre-release suffix`() {
        assertTrue(isNewerVersion("1.7-beta", "1.6"))
    }

    @Test
    fun `handles different segment lengths`() {
        assertTrue(isNewerVersion("2.0", "1.9.9"))
        assertTrue(isNewerVersion("1.6.0.1", "1.6"))
    }

    @Test
    fun `equal with different segment lengths returns false`() {
        assertFalse(isNewerVersion("1.6.0", "1.6"))
        assertFalse(isNewerVersion("1.6", "1.6.0"))
    }

    @Test
    fun `handles empty segments gracefully`() {
        assertFalse(isNewerVersion("", "1.6"))
        assertTrue(isNewerVersion("1.6", ""))
    }

    // ── checkForUpdate tests ──

    @Test
    fun `checkForUpdate returns Available when newer release has APK`() = runTest {
        repository.versionName = "1.5"
        repository.githubRepo = "akvo/test-repo"

        val release = GitHubReleaseDto(
            tagName = "v1.7",
            name = "Release 1.7",
            body = "Bug fixes",
            htmlUrl = "https://github.com/akvo/test-repo/releases/tag/v1.7",
            assets = listOf(
                GitHubAssetDto(
                    name = "app-release.apk",
                    browserDownloadUrl = "https://github.com/akvo/test-repo/releases/download/v1.7/app-release.apk",
                    size = 15_000_000
                )
            )
        )
        coEvery { gitHubApiService.getLatestRelease("akvo", "test-repo") } returns release

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.Available)
        val available = result as UpdateResult.Available
        assertEquals("v1.7", available.release.tagName)
        assertEquals(
            "https://github.com/akvo/test-repo/releases/download/v1.7/app-release.apk",
            available.apkUrl
        )
        assertEquals(15_000_000L, available.apkSize)
    }

    @Test
    fun `checkForUpdate returns UpToDate when same version`() = runTest {
        repository.versionName = "1.6"
        repository.githubRepo = "akvo/test-repo"

        val release = GitHubReleaseDto(
            tagName = "v1.6",
            htmlUrl = "https://github.com/akvo/test-repo/releases/tag/v1.6"
        )
        coEvery { gitHubApiService.getLatestRelease("akvo", "test-repo") } returns release

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.UpToDate)
    }

    @Test
    fun `checkForUpdate returns UpToDate when local is newer`() = runTest {
        repository.versionName = "2.0"
        repository.githubRepo = "akvo/test-repo"

        val release = GitHubReleaseDto(
            tagName = "v1.6",
            htmlUrl = "https://github.com/akvo/test-repo/releases/tag/v1.6"
        )
        coEvery { gitHubApiService.getLatestRelease("akvo", "test-repo") } returns release

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.UpToDate)
    }

    @Test
    fun `checkForUpdate returns Error when release has no APK asset`() = runTest {
        repository.versionName = "1.5"
        repository.githubRepo = "akvo/test-repo"

        val release = GitHubReleaseDto(
            tagName = "v1.7",
            htmlUrl = "https://github.com/akvo/test-repo/releases/tag/v1.7",
            assets = listOf(
                GitHubAssetDto(
                    name = "source.tar.gz",
                    browserDownloadUrl = "https://github.com/akvo/test-repo/releases/download/v1.7/source.tar.gz",
                    size = 5_000_000
                )
            )
        )
        coEvery { gitHubApiService.getLatestRelease("akvo", "test-repo") } returns release

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("No APK found"))
    }

    @Test
    fun `checkForUpdate returns Error when release has empty assets`() = runTest {
        repository.versionName = "1.5"
        repository.githubRepo = "akvo/test-repo"

        val release = GitHubReleaseDto(
            tagName = "v1.7",
            htmlUrl = "https://github.com/akvo/test-repo/releases/tag/v1.7",
            assets = emptyList()
        )
        coEvery { gitHubApiService.getLatestRelease("akvo", "test-repo") } returns release

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("No APK found"))
    }

    @Test
    fun `checkForUpdate returns Error on invalid GITHUB_REPO`() = runTest {
        repository.githubRepo = "invalid-no-slash"

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.Error)
        assertTrue((result as UpdateResult.Error).message.contains("Invalid GITHUB_REPO"))
    }

    @Test
    fun `checkForUpdate returns Error on network failure`() = runTest {
        repository.versionName = "1.5"
        repository.githubRepo = "akvo/test-repo"

        coEvery {
            gitHubApiService.getLatestRelease("akvo", "test-repo")
        } throws IOException("Network unreachable")

        val result = repository.checkForUpdate()

        assertTrue(result is UpdateResult.Error)
        assertEquals("Network unreachable", (result as UpdateResult.Error).message)
    }

    @Test
    fun `checkForUpdate selects first APK asset when multiple exist`() = runTest {
        repository.versionName = "1.5"
        repository.githubRepo = "akvo/test-repo"

        val release = GitHubReleaseDto(
            tagName = "v1.7",
            htmlUrl = "https://github.com/akvo/test-repo/releases/tag/v1.7",
            assets = listOf(
                GitHubAssetDto(
                    name = "README.md",
                    browserDownloadUrl = "https://example.com/README.md",
                    size = 1000
                ),
                GitHubAssetDto(
                    name = "app-release.apk",
                    browserDownloadUrl = "https://example.com/app-release.apk",
                    size = 10_000_000
                ),
                GitHubAssetDto(
                    name = "app-debug.apk",
                    browserDownloadUrl = "https://example.com/app-debug.apk",
                    size = 20_000_000
                )
            )
        )
        coEvery { gitHubApiService.getLatestRelease("akvo", "test-repo") } returns release

        val result = repository.checkForUpdate() as UpdateResult.Available

        assertEquals("https://example.com/app-release.apk", result.apkUrl)
        assertEquals(10_000_000L, result.apkSize)
    }
}