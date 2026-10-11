package com.aether.host.virtualization.util

/**
 * The enforcement boundary for descriptor-driven reflection.
 *
 * A virtualization host eventually needs a handful of framework members that are public at
 * compile time but hidden by `@hide` at runtime. The failure mode of every such bridge is
 * unboundedness: once a helper can resolve any descriptor, any caller can reach any member.
 *
 * This allowlist makes the set finite and reviewable. A member is addressable only when it is
 * declared here, and the declaration records why it exists. Nothing outside the table is
 * reachable, whatever the descriptor says.
 *
 * The table is empty by design at S1: no host capability currently needs a hidden member. Adding
 * one is a deliberate, reviewable act — never a side effect of a new caller.
 */
object HostReflectionAllowlist {

    /** One permitted framework member, with the reason it was granted. */
    data class Entry(
        val declaringClass: String,
        val methodName: String,
        val parameterTypes: List<String>,
        val returnType: String,
        val reason: String,
    ) {
        /** JVM descriptor text, e.g. `attachBaseContext(Landroid/content/Context;)V`. */
        val descriptor: String
            get() = buildString {
                append(methodName).append('(')
                parameterTypes.forEach { append(it) }
                append(')').append(returnType)
            }
    }

    /**
     * Declared members. Internal names use `/` separators, exactly as a descriptor does.
     *
     * Empty at S1. Every future entry must carry a reason that names the host capability that
     * needs it and the review that approved it.
     */
    private val entries: List<Entry> = emptyList()

    /** Every declared member. */
    @JvmStatic
    fun all(): List<Entry> = entries

    /** `true` when the member is declared. Internal names, `/`-separated. */
    @JvmStatic
    fun isAllowed(declaringClass: String, methodName: String, parameterTypes: List<String>): Boolean =
        entries.any { entry ->
            entry.declaringClass == declaringClass &&
                entry.methodName == methodName &&
                entry.parameterTypes == parameterTypes
        }

    /**
     * Resolves a declared member, or returns `null` when it is not on the list.
     * Callers must treat `null` as a hard stop, never as "fall back to unrestricted lookup".
     */
    @JvmStatic
    fun resolve(declaringClass: String, methodName: String, parameterTypes: List<String>): Entry? =
        entries.firstOrNull { entry ->
            entry.declaringClass == declaringClass &&
                entry.methodName == methodName &&
                entry.parameterTypes == parameterTypes
        }

    /** Total declared members; a check that runs asserts this matches the table above. */
    @JvmStatic
    fun size(): Int = entries.size
}
