package io.github.valerait.ghostlink.core.crypto

/**
 * Проверка, что точка лежит на P-256: y² = x³ − 3x + b (mod p).
 *
 * JDK-провайдер cryptography-kotlin принимает любую пару координат, а чужая точка вне кривой
 * позволяет по ответам ECDH вытянуть закрытый ключ (invalid curve attack). В common нет BigInteger,
 * поэтому здесь своя арифметика: 256-битные числа как 8 limb'ов по 32 бита, младший первым.
 * Умножение — сложениями с удвоением, этого хватает для трёх умножений на ключ.
 */
internal object P256 {
    private const val COORD_SIZE = 32
    private const val LIMBS = 8
    private const val LIMB_BITS = 32
    private const val LIMB_BYTES = 4
    private const val LIMB_MASK = 0xFFFFFFFFL
    private const val BYTE_MASK = 0xFFL

    private val P = parse("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")
    private val B = parse("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b")

    /** [raw] — несжатая точка 0x04 || X || Y. */
    fun isOnCurve(raw: ByteArray): Boolean {
        val x = fromBytes(raw, 1)
        val y = fromBytes(raw, 1 + COORD_SIZE)
        if (compare(x, P) >= 0 || compare(y, P) >= 0) return false
        val x3 = mul(mul(x, x), x)
        val threeX = add(add(x, x), x)
        val rhs = add(sub(x3, threeX), B)
        return compare(mul(y, y), rhs) == 0
    }

    private fun parse(hex: String): LongArray = fromBytes(hex.hexToByteArray(), 0)

    private fun fromBytes(
        bytes: ByteArray,
        offset: Int,
    ): LongArray =
        LongArray(LIMBS) { limb ->
            val start = offset + COORD_SIZE - (limb + 1) * LIMB_BYTES
            (0 until LIMB_BYTES).fold(0L) { acc, i -> (acc shl Byte.SIZE_BITS) or (bytes[start + i].toLong() and BYTE_MASK) }
        }

    private fun compare(
        a: LongArray,
        b: LongArray,
    ): Int {
        for (i in LIMBS - 1 downTo 0) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return 0
    }

    /** a + b mod p, где a, b < p. */
    private fun add(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val sum = LongArray(LIMBS)
        var carry = 0L
        for (i in 0 until LIMBS) {
            val s = a[i] + b[i] + carry
            sum[i] = s and LIMB_MASK
            carry = s ushr LIMB_BITS
        }
        return if (carry != 0L || compare(sum, P) >= 0) subRaw(sum, P) else sum
    }

    /** a − b mod p, где a, b < p. */
    private fun sub(
        a: LongArray,
        b: LongArray,
    ): LongArray = if (compare(a, b) >= 0) subRaw(a, b) else add(a, subRaw(P, b))

    /** a − b без модуля; заём из старшего limb'а отбрасывается — так же работает переполнение в [add]. */
    private fun subRaw(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        val diff = LongArray(LIMBS)
        var borrow = 0L
        for (i in 0 until LIMBS) {
            val d = a[i] - b[i] - borrow
            diff[i] = d and LIMB_MASK
            borrow = if (d < 0) 1 else 0
        }
        return diff
    }

    /** a · b mod p: проходим биты b от старшего, удваивая и прибавляя a. */
    private fun mul(
        a: LongArray,
        b: LongArray,
    ): LongArray {
        var acc = LongArray(LIMBS)
        for (bit in LIMBS * LIMB_BITS - 1 downTo 0) {
            acc = add(acc, acc)
            if ((b[bit / LIMB_BITS] ushr (bit % LIMB_BITS)) and 1L == 1L) acc = add(acc, a)
        }
        return acc
    }
}
