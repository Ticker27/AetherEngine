package com.aether.host.virtualization.components.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract checks. The provider is thin marshalling; these tests cover the decisions it makes.
 */
class SystemCallContractTest {

    @Test
    fun tableIsClosedToTheReviewedRuntimeMethods() {
        assertEquals(
            listOf("runtime.status", "runtime.version", "runtime.ping"),
            SystemCallContract.methodNames(),
        )
    }

    @Test
    fun everyAcceptedMethodMapsToTheSameBridgeName() {
        // The bridge name is never caller-supplied, so a mismatch here would mean the table
        // routes a reviewed name to an unreviewed native handler.
        SystemCallContract.methodNames().forEach { name ->
            assertEquals(name, SystemCallContract.bridgeMethodFor(name))
        }
        assertNull(SystemCallContract.bridgeMethodFor("runtime.not.a.method"))
    }

    @Test
    fun unknownAndEmptyMethodsAreRejected() {
        assertTrue(SystemCallContract.validate(null, null) is SystemCallContract.Validation.Rejected)
        assertTrue(SystemCallContract.validate("", null) is SystemCallContract.Validation.Rejected)
        val rejected = SystemCallContract.validate("runtime.exec", null)
        assertTrue(rejected is SystemCallContract.Validation.Rejected)
        assertEquals(
            "unknown method: runtime.exec",
            (rejected as SystemCallContract.Validation.Rejected).reason,
        )
    }

    @Test
    fun zeroArgumentMethodsRejectAnyArgument() {
        val accepted = SystemCallContract.validate("runtime.status", null)
        assertTrue(accepted is SystemCallContract.Validation.Accepted)
        val rejected = SystemCallContract.validate("runtime.status", "unexpected")
        assertTrue(rejected is SystemCallContract.Validation.Rejected)
        assertEquals(
            "method runtime.status takes no argument",
            (rejected as SystemCallContract.Validation.Rejected).reason,
        )
    }

    @Test
    fun acceptanceCheckRejectsUnknownAndNullNames() {
        assertFalse(SystemCallContract.isAccepted("runtime.echo"))
        assertFalse(SystemCallContract.isAccepted(null))
        assertTrue(SystemCallContract.isAccepted("runtime.ping"))
    }

    @Test
    fun rejectionPayloadCarriesTheReasonAndNoData() {
        val payload = SystemCallContract.rejectionPayload("system-call IPC is disabled")
        assertEquals("false", payload[SystemCallContract.KEY_OK])
        assertEquals("system-call IPC is disabled", payload[SystemCallContract.KEY_ERROR])
        assertNull(payload[SystemCallContract.KEY_DATA])
    }
}
