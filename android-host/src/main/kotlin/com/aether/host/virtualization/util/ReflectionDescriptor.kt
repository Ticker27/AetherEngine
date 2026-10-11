package com.aether.host.virtualization.util

/**
 * Pure JVM descriptor codec — the part of a reflection bridge that needs no device.
 *
 * A JVM descriptor is the text form of a method signature, e.g.
 * `"(Landroid/content/Context;I)Landroid/view/View;"`. Parsing it is string work only, so it
 * runs in plain unit tests without an Android runtime.
 *
 * This object deliberately performs **no** invocation and no visibility bypass. It exists so
 * that callers can build and read signatures deterministically; the enforcement boundary lives
 * in [MethodUtils] and [HostReflectionAllowlist].
 */
object ReflectionDescriptor {

    /** Primitive descriptors, in the order the JVM spec defines them. */
    private const val PRIMITIVES = "VZBCSIJFD"

    /** Letters that terminate an object or array type. */
    private const val TERMINATOR = ';'

    data class ParsedMethod(val name: String, val parameters: List<Class<*>>)

    /** `Class<*>` for a primitive, or `null` when [letter] is not a primitive descriptor. */
    @JvmStatic
    fun primitiveClass(letter: Char): Class<*>? = when (letter) {
        'V' -> Void.TYPE
        'Z' -> java.lang.Boolean.TYPE
        'B' -> java.lang.Byte.TYPE
        'C' -> Character.TYPE
        'S' -> java.lang.Short.TYPE
        'I' -> Integer.TYPE
        'J' -> java.lang.Long.TYPE
        'F' -> java.lang.Float.TYPE
        'D' -> java.lang.Double.TYPE
        else -> null
    }

    /** The descriptor letter for a primitive, or `L` for every reference type. */
    @JvmStatic
    fun primitiveLetter(type: Class<*>): Char {
        if (!type.isPrimitive) return 'L'
        return when (type) {
            java.lang.Boolean.TYPE -> 'Z'
            java.lang.Byte.TYPE -> 'B'
            Character.TYPE -> 'C'
            java.lang.Short.TYPE -> 'S'
            Integer.TYPE -> 'I'
            java.lang.Long.TYPE -> 'J'
            java.lang.Float.TYPE -> 'F'
            java.lang.Double.TYPE -> 'D'
            Void.TYPE -> 'V'
            else -> 'L'
        }
    }

    /** Builds a descriptor for [parameters] and [returnType]. */
    @JvmStatic
    fun build(name: String, returnType: Class<*>, vararg parameters: Class<*>): String {
        val builder = StringBuilder(name).append('(')
        for (parameter in parameters) {
            builder.append(typeOf(parameter))
        }
        return builder.append(')').append(typeOf(returnType)).toString()
    }

    /** Descriptor text for a single type, e.g. `I`, `[I`, `Ljava/lang/String;`. */
    @JvmStatic
    fun typeOf(type: Class<*>): String {
        if (type.isPrimitive) return primitiveLetter(type).toString()
        if (type == Void.TYPE) return "V"
        if (type.isArray) return "[".repeat(arrayDepth(type)) + typeOf(type.componentType)
        return "L" + type.name.replace('.', '/') + TERMINATOR
    }

    private fun arrayDepth(type: Class<*>): Int {
        var depth = 0
        var current = type
        while (current.isArray) {
            depth++
            current = current.componentType
        }
        return depth
    }

    /**
     * Splits the parameter list of a descriptor starting at [start], returning the parsed
     * classes and the index just past the closing paren. Array dimensions are honoured.
     */
    @JvmStatic
    fun parseParameters(descriptor: String, start: Int, loader: ClassLoader): ParseResult {
        val parameters = mutableListOf<Class<*>>()
        var index = start
        while (index < descriptor.length) {
            val letter = descriptor[index]
            if (letter == ')') {
                return ParseResult(parameters, index + 1)
            }
            if (letter == '[') {
                var dimensions = 0
                while (index < descriptor.length && descriptor[index] == '[') {
                    dimensions++
                    index++
                }
                val (component, next) = readType(descriptor, index, loader)
                parameters.add(arrayClass(component, dimensions))
                index = next
                continue
            }
            val (type, next) = readType(descriptor, index, loader)
            parameters.add(type)
            index = next
        }
        throw IllegalArgumentException("Unterminated parameter list in descriptor: $descriptor")
    }

    data class ParseResult(val parameters: List<Class<*>>, val nextIndex: Int)

    private fun readType(descriptor: String, start: Int, loader: ClassLoader): Pair<Class<*>, Int> {
        val letter = descriptor[start]
        primitiveClass(letter)?.let { return it to (start + 1) }
        if (letter == '[') {
            var dimensions = 0
            var index = start
            while (index < descriptor.length && descriptor[index] == '[') {
                dimensions++
                index++
            }
            val (component, next) = readType(descriptor, index, loader)
            return arrayClass(component, dimensions) to next
        }
        if (letter != 'L') {
            throw IllegalArgumentException("Unexpected descriptor letter '$letter' at $start")
        }
        val end = descriptor.indexOf(TERMINATOR, start)
        if (end < 0) {
            throw IllegalArgumentException("Unterminated object type in descriptor: $descriptor")
        }
        val internalName = descriptor.substring(start + 1, end)
        return loadClass(internalName, loader) to (end + 1)
    }

    /** Resolves an internal name such as `java/lang/String` or `[I` to a [Class]. */
    @JvmStatic
    fun loadClass(internalName: String, loader: ClassLoader): Class<*> {
        if (internalName.isEmpty()) throw IllegalArgumentException("Empty type name")
        if (internalName[0] == '[') {
            var dimensions = 0
            var index = 0
            while (index < internalName.length && internalName[index] == '[') {
                dimensions++
                index++
            }
            val componentName = internalName.substring(index)
            val component = when (componentName.length) {
                1 -> primitiveClass(componentName[0])
                    ?: throw IllegalArgumentException("Bad array component: $internalName")
                else -> loadClass(componentName, loader)
            }
            return arrayClass(component, dimensions)
        }
        return Class.forName(internalName.replace('/', '.'), false, loader)
    }

    /** `int[][].class` without the Kotlin array-literal gymnastics. */
    @JvmStatic
    fun arrayClass(component: Class<*>, dimensions: Int): Class<*> {
        require(dimensions >= 1) { "dimensions must be positive" }
        var current = component
        repeat(dimensions) {
            current = java.lang.reflect.Array.newInstance(current, 0).javaClass
        }
        return current
    }

    /**
     * Parses `name(params)return` into a name plus parameter classes.
     * Returns `null` when the text is not a well-formed descriptor.
     */
    @JvmStatic
    fun parse(descriptor: String, loader: ClassLoader): ParsedMethod? {
        val open = descriptor.indexOf('(')
        if (open < 0) return null
        val name = descriptor.substring(0, open)
        if (name.isEmpty() || !name.all { it.isJavaIdentifierPart() || it == '$' }) return null
        return try {
            val result = parseParameters(descriptor, open + 1, loader)
            ParsedMethod(name, result.parameters)
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    /** The method name from a descriptor, or `null` when unparseable. */
    @JvmStatic
    fun methodName(descriptor: String): String? {
        val open = descriptor.indexOf('(')
        if (open <= 0) return null
        val name = descriptor.substring(0, open)
        return name.takeIf { it.all { c -> c.isJavaIdentifierPart() || c == '$' } }
    }

    /** The declaring class from a `Lpkg/Cls;method(params)ret` style descriptor. */
    @JvmStatic
    fun declaringClass(descriptor: String, loader: ClassLoader): Class<*>? {
        if (!descriptor.startsWith("L")) return null
        val semicolon = descriptor.indexOf(TERMINATOR)
        if (semicolon < 0) return null
        return try {
            loadClass(descriptor.substring(1, semicolon), loader)
        } catch (error: ClassNotFoundException) {
            null
        }
    }

    /** `true` when the descriptor text is structurally valid for [loader]. */
    @JvmStatic
    fun isValid(descriptor: String, loader: ClassLoader): Boolean =
        parse(descriptor, loader) != null
}
