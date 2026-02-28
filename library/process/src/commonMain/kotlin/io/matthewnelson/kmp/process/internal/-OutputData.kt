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
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmSynthetic

// Used to ensure only asOutputData can instantiate SingleData or SegmentedData
private val INIT = Any()

@Throws(IllegalArgumentException::class, IllegalStateException::class)
internal fun Output.Data.commonInit(init: Any?) {
    check(init == INIT) { "Output.Data cannot be extended" }
    require(size >= 0) { "size[$size] < 0" }
}

@Suppress("ReplaceSizeZeroCheckWithIsEmpty")
internal inline fun Output.Data.commonIsEmpty(): Boolean = size == 0

internal inline fun Output.Data.commonToByteArray(): ByteArray = copyInto(dest = ByteArray(size))

internal inline fun Output.Data.commonToString(): String = "Output.Data[size=$size]@" + hashCode()

@Throws(RuntimeException::class)
internal inline fun Collection<Output.Data?>.commonConsolidate(): Output.Data {
    if (isEmpty()) return Output.Data.empty()
    if (size == 1) return firstOrNull() ?: Output.Data.empty()

    val segments = ArrayList<Bit8Array>(size)
    var total = 0
    var countNonEmpty = 0
    for (data in this) {
        if (data.isNullOrEmpty()) continue
        total += data.size
        if (total < 0) throw RuntimeException("Unable to consolidate Output.Data. Total size exceeds ${Int.MAX_VALUE}.")
        countNonEmpty++
        when (data) {
            is SingleData -> segments.add(data.data)
            is SegmentedData -> segments.addAll(data.segments)
        }
    }

    // Collection contained a single NonEmptyData. Return it instead of creating a new one.
    if (countNonEmpty == 1) firstOrNull { data -> !data.isNullOrEmpty() }?.let { return it }

    return segments.asOutputData()
}

internal inline fun Output.Data.Companion.empty(): Output.Data = emptyList<Bit8Array>().asOutputData()

internal fun Bit8Array.asOutputData(): Output.Data {
    return if (size() <= 0) EmptyData
    else SingleData(data = this, INIT)
}

// Throws if list has 2 or more Bit8Array and any of them have
// a capacity less than 1 (i.e. are empty)
@Throws(IllegalArgumentException::class)
internal fun List<Bit8Array>.asOutputData(): Output.Data {
    if (isEmpty()) return EmptyData
    if (size == 1) return this[0].asOutputData()

    var total = 0
    val cumulativeSizes = IntArray(size)
    val segments = Array(size) { i ->
        val segment = get(i)
        require(segment.size() > 0) { "array at index[$i] has size[${segment.size()}] <= 0" }
        total += segment.size()
        cumulativeSizes[i] = total
        segment
    }

    return SegmentedData(segments, cumulativeSizes, INIT)
}

private object EmptyData: Output.Data(size = 0, INIT) {

    private object EmptyIterator: ByteIterator() {
        override fun hasNext(): Boolean = false
        override fun nextByte(): Byte = throw NoSuchElementException("Index 0 out of bounds for size 0")
    }

    override operator fun get(index: Int): Byte = throw IndexOutOfBoundsException("size == 0")

    override operator fun iterator(): ByteIterator = EmptyIterator
    override operator fun contains(element: Byte): Boolean = false
    override fun containsAll(elements: Collection<Byte>): Boolean = elements.isEmpty()

    override fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ByteArray {
        checkCopyBounds(dest, destOffset, indexStart, indexEnd)
        return dest
    }

    override fun utf8(): String = ""

    override fun equals(other: Any?): Boolean = other is Output.Data && other.isEmpty()
    override fun hashCode(): Int = 1
}

internal sealed class NonEmptyData(size: Int, init: Any?): Output.Data(size, init) {

    @Volatile
    private var _lazyHashCode: Int = UNKNOWN_HASH_CODE
    @Volatile
    private var _lazyUtf8: String? = null

    final override fun containsAll(elements: Collection<Byte>): Boolean {
        elements.forEach { element ->
            if (element !in this) return false
        }
        return true
    }

    final override fun utf8(): String = _lazyUtf8 ?: run {
        val sb = StringBuilder(size)
        UTF8.newEncoderFeed(sb::append).use { feed ->
            when (this) {
                is SingleData -> for (i in data.indices()) {
                    feed.consume(input = data[i])
                }
                is SegmentedData -> for (i in segments.indices) {
                    val segment = segments[i]
                    for (j in segment.indices()) {
                        feed.consume(input = segment[j])
                    }
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

        if (other is SingleData && this is SingleData) {
            for (i in indices) {
                if (other.data[i] != this.data[i]) return false
            }
            return true
        }

        val otherI = other.iterator()
        val thisI = this.iterator()
        repeat(size) {
            if (otherI.nextByte() != thisI.nextByte()) return false
        }

        return true
    }

    final override fun hashCode(): Int {
        var result = _lazyHashCode
        if (result != UNKNOWN_HASH_CODE) return result
        result = 1
        when (this) {
            is SingleData -> for (i in data.indices()) {
                result = result * 31 + data[i].hashCode()
            }
            is SegmentedData -> for (i in segments.indices) {
                val segment = segments[i]
                for (j in segment.indices()) {
                    result = result * 31 + segment[j].hashCode()
                }
            }
        }
        _lazyHashCode = result
        return result
    }

    private companion object {
        private const val UNKNOWN_HASH_CODE = Int.MIN_VALUE
    }
}

internal class SingleData internal constructor(
    @get:JvmSynthetic
    internal val data: Bit8Array,
    init: Any?,
): NonEmptyData(data.size(), init) {
    override operator fun get(index: Int): Byte = data.checkIndexAndGet(index)
    override operator fun iterator(): ByteIterator = data.iterator()
    override operator fun contains(element: Byte): Boolean = element in data
    override fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ByteArray = data.copyInto(dest, destOffset, indexStart, indexEnd, checkBounds = true)
}

internal class SegmentedData internal constructor(
    @get:JvmSynthetic
    internal val segments: Array<Bit8Array>,
    @get:JvmSynthetic
    internal val cumulativeSizes: IntArray,
    init: Any?,
): NonEmptyData(size = cumulativeSizes.last(), init) {

    override operator fun get(index: Int): Byte {
        size.checkIndex(index)
        val iSegment = segmentIndexAt(byteIndex = index)
        val segment = segments[iSegment]
        val offset = cumulativeSizes[iSegment] - segment.size()
        return segment[index - offset]
    }

    override operator fun iterator(): ByteIterator = object : ByteIterator() {

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

    override operator fun contains(element: Byte): Boolean {
        for (i in segments.indices) {
            if (element in segments[i]) return true
        }
        return false
    }

    override fun copyInto(
        dest: ByteArray,
        destOffset: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ByteArray {
        checkCopyBounds(dest, destOffset, indexStart, indexEnd)
        var iSegment = segmentIndexAt(byteIndex = indexStart)
        var offsetSegment = indexStart - (cumulativeSizes[iSegment] - segments[iSegment].size())
        var offsetDest = destOffset
        var remainder = indexEnd - indexStart
        while (remainder > 0) {
            val segment = segments[iSegment++]
            val length = minOf(remainder, segment.size() - offsetSegment)
            segment.copyInto(
                dest,
                destOffset = offsetDest,
                indexStart = offsetSegment,
                indexEnd = offsetSegment + length,
                checkBounds = false,
            )
            offsetSegment = 0 // Will always be 0 after first segment is copied
            offsetDest += length
            remainder -= length
        }
        return dest
    }

    // Assumes byteIndex has been checked to be within 0 until size
    private fun segmentIndexAt(byteIndex: Int): Int {
        val cumulativeSizes = cumulativeSizes
        var i = cumulativeSizes.size / 2
        while (i > 0 && i < cumulativeSizes.size) {
            if (byteIndex >= cumulativeSizes[i]) {
                i++
                continue
            }
            if (byteIndex >= cumulativeSizes[i - 1]) return i
            i--
        }
        return i
    }
}
