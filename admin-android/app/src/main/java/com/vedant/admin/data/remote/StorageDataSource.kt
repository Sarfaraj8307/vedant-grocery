package com.vedant.admin.data.remote

import com.vedant.admin.core.logging.AppLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.client.plugins.onUpload
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.seconds

private const val TAG = "Storage"

/**
 * The **upload seam** over Supabase Storage, for the **public** `product-images`
 * bucket.
 *
 * An interface so the upload path is unit-testable with a fake — the live bucket
 * is **[BLOCKED]** (no admin creds), so this is proven against a fake and said
 * so plainly.
 *
 * The returned value is the **server-confirmed public URL** derived from the
 * path the server actually stored ([io.github.jan.supabase.storage.FileUploadResponse.path]),
 * not the path the caller asked for. A local file pick is never success.
 */
interface StorageDataSource {

    /**
     * Uploads [bytes] to `product-images/[path]` and returns the public URL.
     *
     * @param onProgress receives `0f..1f` as bytes leave the device; called on
     *   the calling coroutine's context.
     * @throws Throwable on any transport or server failure — the caller maps it
     *   to an `AppError` and offers a retry.
     */
    suspend fun uploadProductImage(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): String

    /**
     * Uploads [bytes] to `complaint-images/[path]` and returns the **stored
     * object key**.
     *
     * `complaint-images` is **private**, so there is no public URL to return —
     * the key is what the `complaint_attachments` row records, and a short-lived
     * signed URL is minted on demand via [signedComplaintUrl].
     *
     * The caller **must** pass a path whose first segment is the uploader's
     * `auth.uid()`: the storage RLS policy reads that segment to authorise the
     * owner, so any other shape is rejected by the server (`docs/CONTRACT.md` §6).
     *
     * @throws Throwable on any transport or server failure — including the RLS
     *   denial that a wrong path segment produces.
     */
    suspend fun uploadComplaintAttachment(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): String

    /**
     * Mints a short-lived signed URL for a private `complaint-images` object.
     * Returns `null` when the server refuses (e.g. the object is gone), so the UI
     * degrades to the file name rather than showing a broken link.
     */
    suspend fun signedComplaintUrl(path: String, expiresInSeconds: Long): String?
}

/**
 * The real [StorageDataSource], backed by `client.storage`.
 *
 * Progress is wired through Ktor's `onUpload` hook (`httpOverride` on the upload
 * options), which reports `bytesSentTotal`/`contentLength` as the body streams —
 * so the editor shows genuine byte progress, not a fake animation. `upsert = true`
 * makes a retry of the same path idempotent rather than a `409`.
 */
class SupabaseStorageDataSource(private val client: SupabaseClient) : StorageDataSource {

    override suspend fun uploadProductImage(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): String {
        val bucket = client.storage.from(BUCKET_PRODUCT_IMAGES)
        AppLog.i(TAG, "uploading ${bytes.size} bytes to $BUCKET_PRODUCT_IMAGES/$path")

        val response = bucket.upload(path, bytes) {
            upsert = true
            contentType = ContentType.parse(mimeType)
            httpOverride {
                onUpload { bytesSentTotal, contentLength ->
                    if (contentLength != null && contentLength > 0L) {
                        onProgress((bytesSentTotal.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        }

        // The server may normalise the path; the URL must reflect what it stored.
        val storedPath = response.path.ifBlank { path }
        val publicUrl = bucket.publicUrl(storedPath)
        onProgress(1f)
        AppLog.i(TAG, "upload complete: $publicUrl")
        return publicUrl
    }

    override suspend fun uploadComplaintAttachment(
        path: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit,
    ): String {
        val bucket = client.storage.from(BUCKET_COMPLAINT_IMAGES)
        AppLog.i(TAG, "uploading ${bytes.size} bytes to $BUCKET_COMPLAINT_IMAGES/$path")

        val response = bucket.upload(path, bytes) {
            upsert = true
            contentType = ContentType.parse(mimeType)
            httpOverride {
                onUpload { bytesSentTotal, contentLength ->
                    if (contentLength != null && contentLength > 0L) {
                        onProgress((bytesSentTotal.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        }

        val storedPath = response.path.ifBlank { path }
        onProgress(1f)
        AppLog.i(TAG, "attachment stored at $storedPath")
        return storedPath
    }

    override suspend fun signedComplaintUrl(path: String, expiresInSeconds: Long): String? =
        runCatching {
            client.storage.from(BUCKET_COMPLAINT_IMAGES)
                .createSignedUrl(path, expiresInSeconds.coerceAtLeast(MIN_SIGNED_SECONDS).seconds)
        }.onFailure { AppLog.w(TAG, "could not sign $path", it) }
            .getOrNull()

    companion object {
        /** The public bucket product images live in. */
        const val BUCKET_PRODUCT_IMAGES = "product-images"

        /** The **private** bucket complaint attachments live in. */
        const val BUCKET_COMPLAINT_IMAGES = "complaint-images"

        /** The shortest signed-URL lifetime we will ever ask for. */
        const val MIN_SIGNED_SECONDS = 60L
    }
}
