/*
 * Copyright (c) 2026 Matthew Nelson
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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE", "PropertyName")

package io.matthewnelson.kmp.process.internal

import io.matthewnelson.encoding.core.use
import io.matthewnelson.encoding.core.util.wipe
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.kmp.process.Output
import io.matthewnelson.kmp.process.ReadBuffer
import kotlin.concurrent.Volatile
import kotlin.math.min

private val INIT = Any()

@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun Output.Data.commonInit(init: Any) {
    check(init == INIT) { "Output.Data cannot be extended" }
    require(size >= 0) { "size[$size] < 0" }
}

@Suppress("ReplaceSizeZeroCheckWithIsEmpty")
internal inline fun Output.Data.commonIsEmpty(): Boolean = size == 0

internal inline fun Output.Data.commonBytes(): ByteArray = copyInto(dest = ByteArray(size))

internal inline fun Output.Data.commonToString(): String = "Output.Data[size=$size]@" + hashCode()

@Throws(RuntimeException::class)
internal inline fun Collection<Output.Data?>.commonMerge(
    _segmentsGet: Output.Data.() -> Array<ReadBuffer>,
): Output.Data {
    if (isEmpty()) return emptyList<ReadBuffer>().asOutputData()
    if (size == 1) return first() ?: emptyList<ReadBuffer>().asOutputData()

    val segments = ArrayList<ReadBuffer>(size)
    var total = 0
    var countNonEmpty = 0
    for (data in this) {
        if (data.isNullOrEmpty()) continue
        total += data.size
        if (total < 0) throw RuntimeException("Unable to merge Output.Data. Total size exceeds ${Int.MAX_VALUE}.")
        countNonEmpty++
        segments.addAll(data._segmentsGet())
    }
    // Collection contained a single NonEmptyData. Return it instead of creating a new one.
    if (countNonEmpty == 1) return first { data -> !data.isNullOrEmpty() }!!
    return segments.asOutputData()
}

internal fun ReadBuffer.asOutputData(): Output.Data {
    val cap = capacity()
    return if (cap <= 0) EmptyData
    else SingleData(buf = this, size = cap)
}

// Throws if list has 2 or more ReadBuffer and any of them have
// a capacity less than 1 (i.e. are empty)
@Throws(IllegalArgumentException::class)
internal fun List<ReadBuffer>.asOutputData(): Output.Data {
    if (isEmpty()) return EmptyData
    if (size == 1) return this[0].asOutputData()

    var total = 0
    val sizes = IntArray(size)
    val segments = Array(size) { i ->
        val buf = get(i)
        val cap = buf.capacity()
        require(cap > 0) { "$buf at index[$i] has capacity[$cap] <= 0" }
        total += cap
        sizes[i] = total
        buf
    }

    return SegmentedData(segments, sizes)
}

private object EmptyData: Output.Data(size = 0, segments = emptyArray(), sizes = null, INIT) {

    private object EmptyIterator: ByteIterator() {
        override fun hasNext(): Boolean = false
        override fun nextByte(): Byte = throw NoSuchElementException("Index 0 out of bounds for size 0")
    }

    override fun get(index: Int): Byte = throw IndexOutOfBoundsException("size == 0")

    override fun iterator(): ByteIterator = EmptyIterator
    override fun contains(element: Byte): Boolean = false
    override fun containsAll(elements: Collection<Byte>): Boolean = elements.isEmpty()

    override fun copyInto(dest: ByteArray, destOffset: Int, indexStart: Int, indexEnd: Int): ByteArray {
        checkCopyBounds(dest, destOffset, indexStart, indexEnd)
        return dest
    }
    override fun utf8(): String = ""

    override fun equals(other: Any?): Boolean = other is Output.Data && other.isEmpty()
    override fun hashCode(): Int = 17 * 31 + this::class.hashCode()
}

private sealed class NonEmptyData(
    protected val segments: Array<ReadBuffer>,
    sizes: IntArray?,
    size: Int,
): Output.Data(size, segments, sizes, INIT) {

    @Volatile
    private var _lazyHashCode: Int = UNKNOWN_HASH_CODE
    @Volatile
    protected var _lazyUtf8: String? = null

    final override fun contains(element: Byte): Boolean {
        for (i in segments.indices) {
            val segment = segments[i]
            for (j in 0 until segment.capacity()) {
                if (segment[j] == element) return true
            }
        }
        return false
    }

    final override fun containsAll(elements: Collection<Byte>): Boolean {
        elements.forEach { element -> if (!contains(element)) return false }
        return true
    }

    final override fun equals(other: Any?): Boolean {
        return other is NonEmptyData && other.hashCode() == this.hashCode()
    }

    final override fun hashCode(): Int {
        var result = _lazyHashCode
        if (result != UNKNOWN_HASH_CODE) return result
        result = 17
        result = result * 31 + NonEmptyData::class.hashCode()
        for (i in segments.indices) {
            result = result * 31 + segments[i].hashCode()
        }
        _lazyHashCode = result
        return result
    }

    private companion object {
        private const val UNKNOWN_HASH_CODE = Int.MIN_VALUE
    }
}

private class SingleData(
    private val buf: ReadBuffer,
    size: Int,
): NonEmptyData(segments = arrayOf(buf), sizes = null, size) {

    override fun get(index: Int): Byte = buf[index]

    override fun iterator(): ByteIterator = object : ByteIterator() {
        private var i = 0
        override fun hasNext(): Boolean = i < size
        override fun nextByte(): Byte = if (i < size) buf[i++]
        else throw NoSuchElementException("Index $i out of bounds for size $size")
    }

    override fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ByteArray = buf.copyInto(dest, destOffset, indexStart, indexEnd)

    override fun utf8(): String = _lazyUtf8 ?: buf.utf8().also { _lazyUtf8 = it }
}

private class SegmentedData(
    segments: Array<ReadBuffer>,
    private val sizes: IntArray,
): NonEmptyData(segments, sizes, sizes.last()) {

    override fun get(index: Int): Byte {
        // TODO: binary search sizes for segment index
        var i = index
        for (j in segments.indices) {
            val segment = segments[j]
            val cap = segment.capacity()
            if (i < cap) return segment[i]
            i -= cap
        }
        throw IndexOutOfBoundsException("index[$index] >= size[$size]")
    }

    override fun iterator(): ByteIterator = object : ByteIterator() {

        private var i = 0
        private var j = 0
        private var _segment: ReadBuffer? = segments[j++]

        override fun hasNext(): Boolean = _segment != null

        override fun nextByte(): Byte {
            val segment = _segment ?: throw NoSuchElementException("Index $i out of bounds for size $size")
            val b = segment[i++]
            if (i == segment.capacity()) {
                _segment = segments.elementAtOrNull(j++)
                i = 0
            }
            return b
        }
    }

    override fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ByteArray {
        checkCopyBounds(dest, destOffset, indexStart, indexEnd)
        // TODO: binary search sizes for segment index
        var i = indexStart
        var offset = destOffset
        var remainder = indexEnd - indexStart
        var j = 0
        while (j < segments.size && remainder > 0) {
            val segment = segments[j++]
            val cap = segment.capacity()
            if (i >= cap) {
                i -= cap
                continue
            }
            val len = min(remainder, cap - i)
            segment.copyIntoUnsafe(dest, offset, i, i + len)
            i = 0
            offset += len
            remainder -= len
        }
        return dest
    }

    override fun utf8(): String = _lazyUtf8 ?: run {
        val sb = StringBuilder(size)
        UTF8.newEncoderFeed(sb::append).use { feed ->
            for (i in segments.indices) {
                val segment = segments[i]
                for (j in 0 until segment.capacity()) {
                    feed.consume(input = segment[j])
                }
            }
        }
        val s = sb.toString()
        _lazyUtf8 = s
        sb.wipe()
        s
    }
}
