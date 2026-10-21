package com.contentria.media.application.service

import com.contentria.media.application.provided.CleanupReport
import com.contentria.media.application.required.MediaRepository
import com.contentria.media.application.required.ObjectStorage
import com.contentria.media.domain.image.Media
import com.contentria.shared.persistence.AuditableEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ImageCleanupServiceTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val uploader = UUID.randomUUID()

    /** The rows, keyed by id. Finds the same way the named queries do. */
    private val rows = mutableMapOf<UUID, Media>()

    private val repository = object : MediaRepository {
        override fun save(media: Media) = media.also { rows[it.id] = it }
        override fun findById(id: UUID) = rows[id]
        override fun delete(media: Media) { rows.remove(media.id) }
        override fun findByIdIn(ids: Collection<UUID>) = ids.mapNotNull(rows::get)
        override fun sumSizePreparedSince(uploaderId: UUID, since: Instant) = 0L
        override fun findAttachedTo(postId: UUID) = rows.values.filter { postId in it.attachedPostIds }
        override fun findExpiredUploadIds(createdBefore: Instant) =
            rows.values.filter { !it.isReady() && it.createdAt < createdBefore }.map { it.id }
        override fun findUnusedIds(unattachedBefore: Instant) =
            rows.values.filter { it.unattachedSince?.isBefore(unattachedBefore) == true }.map { it.id }
        override fun findLockedById(id: UUID) = beforeLock(id).let { rows[id] }
    }

    /** Runs as the lock is taken, to change an image between listing and removing it. */
    private var beforeLock: (UUID) -> Unit = {}

    private val files = mutableSetOf<String>()
    private val failingKeys = mutableSetOf<String>()

    private val storage = object : ObjectStorage {
        override fun uploadUrl(key: String, contentType: String, sizeBytes: Long, validFor: Duration) = URI.create("http://storage.test/$key")
        override fun read(key: String): ByteArray? = null
        override fun write(key: String, bytes: ByteArray, contentType: String) { files += key }
        override fun delete(key: String) {
            check(key !in failingKeys) { "storage refused" }
            files -= key
        }
    }

    /** Commits and rolls back nothing; the rows above are the state. */
    private val transactions = TransactionTemplate(object : AbstractPlatformTransactionManager() {
        override fun doGetTransaction() = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    })

    private val service = ImageCleanupService(repository, storage, transactions, Clock.fixed(now, ZoneOffset.UTC))

    /** An image made [age] ago, ready [unusedFor] ago when given; its file is in the storage. */
    private fun image(age: Duration, unusedFor: Duration? = null): Media {
        val media = Media.prepare(uploader, "image/png", 10, "a.png")
        AGE.set(media, now.minus(age))
        unusedFor?.let { media.complete(now.minus(it)) }
        files += media.storedKey
        return repository.save(media)
    }

    @Test
    @DisplayName("하루가 지난 업로드와 일주일 동안 쓰이지 않은 이미지는 파일과 기록을 지운다. 그 안쪽은 남긴다")
    fun `expired images go`() {
        val oldUpload = image(Duration.ofHours(25))
        val freshUpload = image(Duration.ofHours(23))
        val unused = image(Duration.ofDays(10), unusedFor = Duration.ofDays(8))
        val recentlyUnused = image(Duration.ofDays(10), unusedFor = Duration.ofDays(6))
        val attached = image(Duration.ofDays(10), unusedFor = Duration.ofDays(8)).also { it.attachTo(UUID.randomUUID(), uploader) }

        val report = service.cleanUp()

        assertThat(report.removed).isEqualTo(2)
        assertThat(report.failed).isZero()
        assertThat(rows.keys).containsExactlyInAnyOrder(freshUpload.id, recentlyUnused.id, attached.id)
        assertThat(files).containsExactlyInAnyOrder(freshUpload.storedKey, recentlyUnused.storedKey, attached.storedKey)
        assertThat(rows).doesNotContainKeys(oldUpload.id, unused.id)
    }

    @Test
    @DisplayName("파일을 지우지 못하면 기록을 남겨 다음에 다시 지우고, 나머지는 계속한다")
    fun `a file that will not go keeps its record`() {
        val stuck = image(Duration.ofHours(30))
        val other = image(Duration.ofHours(30))
        failingKeys += stuck.storedKey

        val report = service.cleanUp()

        assertThat(report).isEqualTo(CleanupReport(removed = 1, failed = 1))
        assertThat(rows.keys).containsExactly(stuck.id)
        assertThat(files).containsExactly(stuck.storedKey)
        assertThat(rows).doesNotContainKey(other.id)

        failingKeys.clear()
        assertThat(service.cleanUp().removed).isEqualTo(1)
        assertThat(rows).isEmpty()
    }

    @Test
    @DisplayName("목록에 오른 뒤 잠그기 전에 글에 연결된 이미지는 지우지 않는다")
    fun `decided again under the lock`() {
        val image = image(Duration.ofDays(10), unusedFor = Duration.ofDays(8))
        beforeLock = { rows[it]?.attachTo(UUID.randomUUID(), uploader) }

        assertThat(service.cleanUp().removed).isZero()
        assertThat(rows.keys).containsExactly(image.id)
        assertThat(files).containsExactly(image.storedKey)
    }

    private companion object {
        /** createdAt is set by JPA on persist; a test sets it the same way, through the field. */
        val AGE = AuditableEntity::class.java.getDeclaredField("createdAt").apply { isAccessible = true }
    }
}
