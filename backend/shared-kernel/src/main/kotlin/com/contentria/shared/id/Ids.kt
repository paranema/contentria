package com.contentria.shared.id

import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Identifier generation for every aggregate in every context.
 *
 * Identifiers are assigned when the object is constructed, not by the database on insert,
 * so an aggregate is never in a state where it exists but has no identity. That removes the
 * nullable-id pattern the previous model needed.
 *
 * UUIDv7 carries a millisecond timestamp in its leading bits, so identifiers sort in creation
 * order and primary key inserts land at the right edge of the B-tree instead of scattering
 * across it the way UUIDv4 does.
 *
 * The standard library generator is monotonic: it holds the timestamp and a counter in a single
 * atomic word, so identifiers minted within the same millisecond, from any thread, still come
 * out strictly increasing. A generator that re-randomises the trailing bits on every call gives
 * up ordering inside a millisecond, which is exactly the resolution a burst of writes lands in.
 *
 * The return type is [java.util.UUID] because that is what Hibernate maps to a Postgres `uuid`
 * column. [Uuid] is a standard library type Hibernate has no mapping for, so it stays confined
 * to this file rather than leaking into entity fields and dragging an AttributeConverter along.
 */
object Ids {

    @OptIn(ExperimentalUuidApi::class)
    fun newId(): UUID = Uuid.generateV7().toJavaUuid()
}
