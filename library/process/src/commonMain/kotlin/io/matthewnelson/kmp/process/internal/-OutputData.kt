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

internal inline fun Output.Data.commonToByteArray(): ByteArray = copyInto(dest = ByteArray(size))

internal inline fun Output.Data.commonToString(): String = "Output.Data[size=$size]@" + hashCode()

@Throws(RuntimeException::class)
internal inline fun Collection<Output.Data?>.commonMerge(
    _segmentsGet: Output.Data.() -> Array<Bit8Array>,
): Output.Data {
    if (isEmpty()) return Output.Data.empty()
    if (size == 1) return firstOrNull() ?: Output.Data.empty()

    val segments = ArrayList<Bit8Array>(size)
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
    if (countNonEmpty == 1) firstOrNull { data -> !data.isNullOrEmpty() }?.let { return it }
    return segments.asOutputData()
}

internal inline fun Output.Data.Companion.empty(): Output.Data = emptyList<Bit8Array>().asOutputData()

internal fun Bit8Array.asOutputData(): Output.Data {
    return if (size() <= 0) EmptyData
    else SingleData(data = this)
}

// Throws if list has 2 or more Bit8Array and any of them have
// a capacity less than 1 (i.e. are empty)
@Throws(IllegalArgumentException::class)
internal fun List<Bit8Array>.asOutputData(): Output.Data {
    if (isEmpty()) return EmptyData
    if (size == 1) return this[0].asOutputData()

    var total = 0
    val sizes = IntArray(size)
    val segments = Array(size) { i ->
        val segment = get(i)
        require(segment.size() > 0) { "array at index[$i] has size[${segment.size()}] <= 0" }
        total += segment.size()
        sizes[i] = total
        segment
    }

    return SegmentedData(segments, sizes)
}

// TODO: REMOVE
internal fun ReadBuffer.asOutputDataREMOVE00(): Output.Data {
    val cap = capacity()
    return if (cap <= 0) EmptyData
    else SingleData(data = Bit8Array(size = cap) { i -> this[i] })
}

// TODO: REMOVE
internal fun List<ReadBuffer>.asOutputDataREMOVE00(): Output.Data {
    if (isEmpty()) return EmptyData
    if (size == 1) return this[0].asOutputDataREMOVE00()

    var total = 0
    val sizes = IntArray(size)
    val segments = Array(size) { i ->
        val buf = get(i)
        val cap = buf.capacity()
        require(cap > 0) { "$buf at index[$i] has capacity[$cap] <= 0" }
        total += cap
        sizes[i] = total
        Bit8Array(cap) { j -> buf[j] }
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
    override fun hashCode(): Int = 1
}

private sealed class NonEmptyData(
    protected val segments: Array<Bit8Array>,
    sizes: IntArray?,
    size: Int,
): Output.Data(size, segments, sizes, INIT) {

    @Volatile
    private var _lazyHashCode: Int = UNKNOWN_HASH_CODE
    @Volatile
    private var _lazyUtf8: String? = null

    final override fun contains(element: Byte): Boolean {
        for (i in segments.indices) {
            val segment = segments[i]
            for (j in segment.indices()) {
                if (segment[j] == element) return true
            }
        }
        return false
    }

    final override fun containsAll(elements: Collection<Byte>): Boolean {
        elements.forEach { element -> if (!contains(element)) return false }
        return true
    }

    final override fun utf8(): String = _lazyUtf8 ?: run {
        val sb = StringBuilder(size)
        UTF8.newEncoderFeed(sb::append).use { feed ->
            for (i in segments.indices) {
                val segment = segments[i]
                for (j in segment.indices()) {
                    feed.consume(input = segment[j])
                }
            }
        }
        val s = sb.toString()
        _lazyUtf8 = s
        sb.wipe()
        s
    }

    final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is NonEmptyData) return false
        if (other.size != this.size) return false

        if (other._lazyHashCode != UNKNOWN_HASH_CODE) {
            if (this._lazyHashCode != UNKNOWN_HASH_CODE) {
                if (other._lazyHashCode != this._lazyHashCode) return false
            }
        }

        var oI = 0
        var oISegment = 0
        var oSegment = other.segments[oISegment++]

        var tI = 0
        var tISegment = 0
        var tSegment = this.segments[tISegment++]
        repeat(size) { _ ->
            if (oI == oSegment.size()) {
                oSegment = other.segments[oISegment++]
                oI = 0
            }
            if (tI == tSegment.size()) {
                tSegment = this.segments[tISegment++]
                tI = 0
            }
            if (oSegment[oI++] != tSegment[tI++]) return false
        }

        return true
    }

    final override fun hashCode(): Int {
        var result = _lazyHashCode
        if (result != UNKNOWN_HASH_CODE) return result
        result = 1
        for (i in segments.indices) {
            val segment = segments[i]
            for (j in segment.indices()) {
                result = result * 31 + segment[j].hashCode()
            }
        }
        _lazyHashCode = result
        return result
    }

    private companion object {
        private const val UNKNOWN_HASH_CODE = Int.MIN_VALUE
    }
}

private class SingleData(
    private val data: Bit8Array,
): NonEmptyData(segments = arrayOf(data), sizes = null, size = data.size()) {

    override fun get(index: Int): Byte = data[index]

    override fun iterator(): ByteIterator = object : ByteIterator() {
        private val _size = size
        private val _data = data
        private var i = 0
        override fun hasNext(): Boolean = i < _size
        override fun nextByte(): Byte = if (i < _size) _data[i++]
        else throw NoSuchElementException("Index $i out of bounds for size $_size")
    }

    override fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ByteArray = data.copyInto(dest, destOffset, indexStart, indexEnd, checkBounds = true)
}

private class SegmentedData(
    segments: Array<Bit8Array>,
    private val sizes: IntArray,
): NonEmptyData(segments, sizes, sizes.last()) {

    override fun get(index: Int): Byte {
        // TODO: binary search sizes for segment index
        var i = index
        for (j in segments.indices) {
            val segment = segments[j]
            if (i < segment.size()) return segment[i]
            i -= segment.size()
        }
        throw IndexOutOfBoundsException("index[$index] >= size[$size]")
    }

    override fun iterator(): ByteIterator = object : ByteIterator() {

        private val _size = size
        private val _segments = segments
        private var i = 0
        private var j = 0
        private var _segment: Bit8Array? = _segments[j++]

        override fun hasNext(): Boolean = _segment != null

        override fun nextByte(): Byte {
            val segment = _segment ?: throw NoSuchElementException("Index $_size out of bounds for size $_size")
            val b = segment[i++]
            if (i == segment.size()) {
                _segment = _segments.elementAtOrNull(j++)
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
            if (i >= segment.size()) {
                i -= segment.size()
                continue
            }
            val len = min(remainder, segment.size() - i)
            segment.copyInto(dest, offset, i, i + len, checkBounds = false)
            i = 0
            offset += len
            remainder -= len
        }
        return dest
    }
}
