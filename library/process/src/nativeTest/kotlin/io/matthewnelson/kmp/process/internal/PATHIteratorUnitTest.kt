/*
 * Copyright (c) 2025 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.process.internal

import io.matthewnelson.kmp.file.SysPathSep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PATHIteratorUnitTest {

    @Test
    fun givenPath_whenSeparatorBetween_thenIteratesFor2AsExpected() {
        val i = PATHIterator(PATH = "/usr/bin${SysPathSep}/something/else")
        assertTrue(i.hasNext())
        assertEquals("/usr/bin", i.next())
        assertTrue(i.hasNext())
        assertEquals("/something/else", i.next())
        assertFalse(i.hasNext())
        assertFailsWith<NoSuchElementException> { i.next() }
    }

    @Test
    fun givenPath_whenSeparatorStart_thenIteratesFor2AsExpected() {
        val i = PATHIterator(PATH = "${SysPathSep}/usr/bin")
        assertTrue(i.hasNext())
        assertEquals("", i.next())
        assertTrue(i.hasNext())
        assertEquals("/usr/bin", i.next())
        assertFalse(i.hasNext())
    }

    @Test
    fun givenPath_whenSeparatorEnd_thenIteratesFor2AsExpected() {
        val i = PATHIterator(PATH = "/usr/bin${SysPathSep}")
        assertTrue(i.hasNext())
        assertEquals("/usr/bin", i.next())
        assertTrue(i.hasNext())
        assertEquals("", i.next())
        assertFalse(i.hasNext())
    }

    @Test
    fun givenPath_whenEmpty_thenHasSingleEmptyValue() {
        val i = PATHIterator(PATH = "")
        assertTrue(i.hasNext())
        assertEquals("", i.next())
        assertFalse(i.hasNext())
    }

    @Test
    fun givenPath_whenSEP_thenHas2EmptyValues() {
        val i = PATHIterator(PATH = "$SysPathSep")
        assertTrue(i.hasNext())
        assertEquals("", i.next())
        assertTrue(i.hasNext())
        assertEquals("", i.next())
        assertFalse(i.hasNext())
    }
}
