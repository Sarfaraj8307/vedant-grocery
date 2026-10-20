package com.vedant.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one rule that must never be broken by convenience: **the APK
 * carries only the publishable key.**
 *
 * The guard is written so it can actually fail. [containsServiceRoleMarker] is
 * exercised against a deliberately injected fake key (which must be detected),
 * and the resource scan asserts it really located the project tree — so a broken
 * working-directory assumption fails the test instead of silently passing.
 */
class SecurityKeyHygieneTest {

    // --- BuildConfig --------------------------------------------------------

    @Test
    fun `the anon key is present and is not a service-role key`() {
        val key = BuildConfig.SUPABASE_ANON_KEY
        assertTrue("SUPABASE_ANON_KEY must not be blank", key.isNotBlank())
        assertFalse(
            "SUPABASE_ANON_KEY carries a service-role marker",
            containsServiceRoleMarker(key),
        )
    }

    @Test
    fun `the anon key looks publishable or anon-role`() {
        val key = BuildConfig.SUPABASE_ANON_KEY
        val looksPublishable = key.startsWith("sb_publishable_") ||
            key.contains("\"role\":\"anon\"") ||
            key.startsWith("eyJ")
        assertTrue(
            "SUPABASE_ANON_KEY is neither a publishable key nor an anon JWT: " +
                "'${key.take(12)}…'",
            looksPublishable,
        )
    }

    @Test
    fun `no BuildConfig field leaks a service-role key`() {
        BuildConfig::class.java.declaredFields.forEach { field ->
            field.isAccessible = true
            val value = field.get(null)?.toString().orEmpty()
            assertFalse(
                "BuildConfig.${field.name} leaks a service-role marker",
                containsServiceRoleMarker(value),
            )
        }
    }

    // --- Resources ----------------------------------------------------------

    @Test
    fun `the resource scan actually inspects the project`() {
        val root = mainSourceRoot()
        assertNotNull(
            "could not locate src/main from working dir '${File(".").absolutePath}'",
            root,
        )
        assertTrue(
            "AndroidManifest.xml not found under ${root?.path}",
            File(root, "AndroidManifest.xml").isFile,
        )
    }

    @Test
    fun `no resource or manifest file contains a service-role marker`() {
        val leaks = scanMainTree()
        assertTrue("service-role marker found in: $leaks", leaks.isEmpty())
    }

    // --- Falsifiability -----------------------------------------------------

    @Test
    fun `the scanner detects an injected service-role key`() {
        val injected = "SUPABASE_SERVICE_ROLE_KEY=" +
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.service_role.deadbeef"
        assertTrue("scanner missed an injected service-role key", containsServiceRoleMarker(injected))
        assertTrue(containsServiceRoleMarker("key: service-role-xyz"))
    }

    @Test
    fun `the scanner does not flag the real publishable key`() {
        assertFalse(
            containsServiceRoleMarker("sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH"),
        )
    }

    // --- Helpers ------------------------------------------------------------

    private fun mainSourceRoot(): File? =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }

    private fun scanMainTree(): List<String> {
        val root = mainSourceRoot() ?: return emptyList()
        val targets = mutableListOf<File>()
        listOf("res", "assets").forEach { dir ->
            File(root, dir).takeIf { it.isDirectory }?.walkTopDown()?.forEach { file ->
                if (file.isFile) targets += file
            }
        }
        File(root, "AndroidManifest.xml").takeIf { it.isFile }?.let { targets += it }

        return targets
            .filter { file ->
                val text = runCatching { file.readText() }.getOrDefault("")
                containsServiceRoleMarker(text)
            }
            .map { it.path }
    }

    private companion object {
        private val SERVICE_ROLE_MARKERS = listOf(
            "service_role",
            "service-role",
            "SERVICE_ROLE_KEY",
            "SUPABASE_SERVICE_ROLE",
        )

        fun containsServiceRoleMarker(text: String): Boolean =
            SERVICE_ROLE_MARKERS.any { text.contains(it, ignoreCase = true) }
    }
}
