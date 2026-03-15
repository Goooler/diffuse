package com.jakewharton.diffuse.io

import kotlin.math.absoluteValue
import me.saket.bytesize.binaryBytes
import me.saket.bytesize.decimalBytes

enum class SizeFormat {
  Binary,
  Decimal,
}

@JvmInline
value class Size(val bytes: Long) : Comparable<Size> {
  override fun toString(): String = toString(SizeFormat.Binary)

  fun toString(format: SizeFormat = SizeFormat.Binary): String {
    val sign = if (bytes >= 0) "" else "-"
    val abs = bytes.absoluteValue
    return sign +
      when (format) {
        SizeFormat.Binary -> abs.binaryBytes
        SizeFormat.Decimal -> abs.decimalBytes
      }
  }

  override fun compareTo(other: Size) = bytes.compareTo(other.bytes)

  operator fun plus(other: Size) = Size(bytes + other.bytes)

  operator fun minus(other: Size) = Size(bytes - other.bytes)

  operator fun unaryMinus() = Size(-bytes)

  val absoluteValue
    get() = Size(bytes.absoluteValue)

  companion object {
    val ZERO = Size(0)
  }
}
