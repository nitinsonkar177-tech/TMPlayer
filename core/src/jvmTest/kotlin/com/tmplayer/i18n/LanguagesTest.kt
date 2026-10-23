package com.tmplayer.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LanguagesTest {

    @Test
    fun `only english ships`() {
        assertEquals(listOf("en"), Languages.tags)
        assertFalse(Languages.isRtl("en"))
    }

    @Test
    fun `every resolution lands on english`() {
        assertEquals("en", Languages.resolve("de", listOf("fr-FR")))
        assertEquals("en", Languages.resolve("", listOf("fr-FR")))
        assertEquals("en", Languages.resolve(null, listOf("hi")))
    }

    @Test
    fun `no system tag matches another language`() {
        assertEquals("en", Languages.match("en-US"))
        assertEquals("en", Languages.match("en"))
        assertNull(Languages.match("hi"))
        assertNull(Languages.match("zh-Hans-CN"))
    }
}
