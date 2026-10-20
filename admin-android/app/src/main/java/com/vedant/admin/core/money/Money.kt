package com.vedant.admin.core.money

/**
 * An exact rupee amount stored as **paise** (1 rupee = 100 paise).
 *
 * Postgres stores money as `numeric(12,2)`. The client must never use `Double`:
 * binary floating point cannot represent 0.1 exactly, and a bill that is off by
 * a paisa is a bug the owner will notice. Integer paise gives exact arithmetic
 * and parity with the PWA's `src/lib/money.ts`.
 *
 * Money here is **display-only**. The app never computes an amount it then sends
 * to the server — `admin_reject_order_item` / `admin_restore_order_item` accept
 * no amount (CLAUDE.md rule 5). `Money` exists to parse, compare and render
 * what the server returned.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(paise + other.paise)

    operator fun minus(other: Money): Money = Money(paise - other.paise)

    operator fun unaryMinus(): Money = Money(-paise)

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    /** True when this amount is exactly zero. */
    val isZero: Boolean get() = paise == 0L

    /** True when this amount is negative (e.g. a discount adjustment). */
    val isNegative: Boolean get() = paise < 0L

    /**
     * Human display, e.g. `₹1,234.50` or `-₹132.50`.
     *
     * Uses the Indian digit grouping (last three, then twos) because that is how
     * the amount is read in the store. **Display only** — never re-parsed for a
     * value that is sent to the server.
     */
    fun format(): String {
        val absolute = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = absolute / 100
        val fraction = (absolute % 100).toString().padStart(2, '0')
        val sign = if (paise < 0L) "-" else ""
        return "$sign₹${groupIndian(rupees)}.$fraction"
    }

    /**
     * A **machine-parseable** decimal string, e.g. `152.00` / `-132.50` — no
     * currency symbol and no digit grouping.
     *
     * This is the counterpart of [parse] and is what [MoneySerializer] writes, so
     * a value round-trips exactly and can be sent to a `numeric(12,2)` column.
     * [format] is for humans; this is for machines.
     */
    fun toPlainString(): String {
        val absolute = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = absolute / 100
        val fraction = (absolute % 100).toString().padStart(2, '0')
        val sign = if (paise < 0L) "-" else ""
        return "$sign$rupees.$fraction"
    }

    companion object {
        val ZERO: Money = Money(0)

        /**
         * Parses a money literal **tolerantly** of the formats PostgREST can
         * emit: `152`, `152.0`, `"152.00"`, `-132.50`, and also `₹1,234.50`.
         *
         * More than two decimal places are rounded **half-up** on the third
         * digit, so `152.125` becomes `152.13`. Blank or non-numeric input throws
         * — a malformed amount is a data problem, not something to silently turn
         * into zero.
         */
        fun parse(text: String): Money {
            val raw = text.trim()
            require(raw.isNotEmpty()) { "Money.parse: blank input" }

            var body = raw.removePrefix("₹").replace(",", "").trim()
            var negative = false
            when {
                body.startsWith("-") -> {
                    negative = true
                    body = body.substring(1)
                }
                body.startsWith("+") -> body = body.substring(1)
            }
            require(body.isNotEmpty()) { "Money.parse: no digits in '$text'" }
            require(body.count { it == '.' } <= 1) { "Money.parse: malformed '$text'" }

            val dot = body.indexOf('.')
            val integerPart = if (dot >= 0) body.substring(0, dot) else body
            val fractionPart = if (dot >= 0) body.substring(dot + 1) else ""

            require(integerPart.all { it.isDigit() }) { "Money.parse: bad integer part in '$text'" }
            require(fractionPart.all { it.isDigit() }) { "Money.parse: bad fraction in '$text'" }
            require(integerPart.isNotEmpty() || fractionPart.isNotEmpty()) {
                "Money.parse: no digits in '$text'"
            }

            val rupees = if (integerPart.isEmpty()) 0L else integerPart.toLong()
            // Pad to exactly three digits: "5" -> "500", "50" -> "500", "" -> "000".
            val fraction = fractionPart.padEnd(3, '0').take(3)
            var paise = fraction.substring(0, 2).toLong()
            if (fraction[2].digitToInt() >= 5) paise += 1

            val total = rupees * 100L + paise
            return Money(if (negative) -total else total)
        }

        /** Convenience for whole rupees, mainly used in tests and seeds. */
        fun ofRupees(rupees: Long): Money = Money(rupees * 100L)

        /** Indian grouping: 1,23,45,678. */
        private fun groupIndian(value: Long): String {
            val digits = value.toString()
            if (digits.length <= 3) return digits
            val lastThree = digits.substring(digits.length - 3)
            val rest = digits.substring(0, digits.length - 3)
            val groups = ArrayDeque<String>()
            var index = rest.length
            while (index > 0) {
                val start = maxOf(0, index - 2)
                groups.addFirst(rest.substring(start, index))
                index = start
            }
            return groups.joinToString(",") + "," + lastThree
        }
    }
}
