package com.aether.host.virtualization.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Descriptor codec checks. Everything here runs on the JVM with no Android runtime, which is the
 * point: the parse/build path is the part a reflection bridge must get exactly right, and the
 * part most often only ever exercised on a device.
 */
class ReflectionDescriptorTest {

    private val loader: ClassLoader = javaClass.classLoader

    @Test
    fun primitiveLettersMatchTheJvmSpecOrder() {
        assertEquals('V', ReflectionDescriptor.primitiveLetter(Void.TYPE))
        assertEquals('Z', ReflectionDescriptor.primitiveLetter(java.lang.Boolean.TYPE))
        assertEquals('B', ReflectionDescriptor.primitiveLetter(java.lang.Byte.TYPE))
        assertEquals('C', ReflectionDescriptor.primitiveLetter(Character.TYPE))
        assertEquals('S', ReflectionDescriptor.primitiveLetter(java.lang.Short.TYPE))
        assertEquals('I', ReflectionDescriptor.primitiveLetter(Integer.TYPE))
        assertEquals('J', ReflectionDescriptor.primitiveLetter(java.lang.Long.TYPE))
        assertEquals('F', ReflectionDescriptor.primitiveLetter(java.lang.Float.TYPE))
        assertEquals('D', ReflectionDescriptor.primitiveLetter(java.lang.Double.TYPE))
        assertEquals('L', ReflectionDescriptor.primitiveLetter(String::class.java))
    }

    @Test
    fun primitiveClassesRoundTripThroughLetters() {
        for (letter in "VZBCSIJFD") {
            assertNotNull("letter $letter must map to a primitive", ReflectionDescriptor.primitiveClass(letter))
            assertEquals(letter, ReflectionDescriptor.primitiveLetter(ReflectionDescriptor.primitiveClass(letter)!!))
        }
        assertNull(ReflectionDescriptor.primitiveClass('L'))
    }

    @Test
    fun buildsDescriptorForMixedParameters() {
        val descriptor = ReflectionDescriptor.build(
            "attach",
            String::class.java,
            Integer.TYPE,
            java.lang.Boolean.TYPE,
            Array<String>::class.java,
        )
        assertEquals("attach(IZ[Ljava/lang/String;)Ljava/lang/String;", descriptor)
    }

    @Test
    fun parsesDescriptorBackIntoParameterClasses() {
        val parsed = ReflectionDescriptor.parse("attach(IZ[Ljava/lang/String;)Ljava/lang/String;", loader)
        assertNotNull(parsed)
        assertEquals("attach", parsed!!.name)
        assertEquals(3, parsed.parameters.size)
        assertEquals(Integer.TYPE, parsed.parameters[0])
        assertEquals(java.lang.Boolean.TYPE, parsed.parameters[1])
        assertEquals(Array<String>::class.java, parsed.parameters[2])
    }

    @Test
    fun parsesObjectAndArrayDescriptors() {
        val result = ReflectionDescriptor.parseParameters(
            "(Ljava/lang/String;[I[[DLjava/lang/Integer;)V",
            1,
            loader,
        )
        assertEquals(4, result.parameters.size)
        assertEquals(String::class.java, result.parameters[0])
        assertEquals(Array<Int>::class.java, result.parameters[1])
        assertEquals(Array<Array<Double>>::class.java, result.parameters[2])
        assertEquals(Integer::class.java, result.parameters[3])
    }

    @Test
    fun rejectsMalformedDescriptorsInsteadOfGuessing() {
        assertNull(ReflectionDescriptor.parse("noParens", loader))
        assertNull(ReflectionDescriptor.parse("(unterminated", loader))
        assertNull(ReflectionDescriptor.parse("name(X)V", loader))
        assertNull(ReflectionDescriptor.parse("name(Lmissing/semicolon)V", loader))
        assertNull(ReflectionDescriptor.methodName("(leadingParen)V"))
        assertNull(ReflectionDescriptor.methodName(""))
    }

    @Test
    fun methodNameAndDeclaringClassExtractFromCombinedDescriptor() {
        assertEquals("onCreate", ReflectionDescriptor.methodName("onCreate(Landroid/os/Bundle;)V"))
        val owner = ReflectionDescriptor.declaringClass(
            "Lcom/aether/host/virtualization/activity/VirtualActivity;onCreate(Landroid/os/Bundle;)V",
            loader,
        )
        assertNull("a bare method descriptor has no declaring class", owner)

        val direct = ReflectionDescriptor.declaringClass(
            "Ljava/lang/String;",
            loader,
        )
        assertEquals(String::class.java, direct)
    }

    @Test
    fun arrayClassBuildsTheRequestedDimensions() {
        assertEquals(Array<Int>::class.java, ReflectionDescriptor.arrayClass(Integer.TYPE, 1))
        assertEquals(Array<Array<Int>>::class.java, ReflectionDescriptor.arrayClass(Integer.TYPE, 2))
        assertEquals(Array<Array<Array<String>>>::class.java, ReflectionDescriptor.arrayClass(String::class.java, 3))
    }

    @Test
    fun validityCheckAcceptsOnlyWellFormedDescriptors() {
        assertTrue(ReflectionDescriptor.isValid("getVersion()Ljava/lang/String;", loader))
        assertFalse(ReflectionDescriptor.isValid("getVersion(", loader))
    }
}
