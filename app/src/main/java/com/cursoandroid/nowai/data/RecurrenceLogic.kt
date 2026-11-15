package com.cursoandroid.nowai.data

import java.time.Instant
import java.time.ZoneId

object RecurrenceLogic {

    /** Calcula o próximo vencimento a partir de um momento (em millis). */
    fun calculateNextDue(from: Long, every: Int, unit: RepeatUnit): Long {
        val date = Instant.ofEpochMilli(from)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val next = when (unit) {
            RepeatUnit.DAYS -> date.plusDays(every.toLong())
            RepeatUnit.WEEKS -> date.plusWeeks(every.toLong())
            RepeatUnit.MONTHS -> date.plusMonths(every.toLong())
            RepeatUnit.YEARS -> date.plusYears(every.toLong())
        }

        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Marca o item como concluído agora e recalcula o próximo vencimento. */
    fun complete(item: Item, now: Long = System.currentTimeMillis()): Item {
        val every = item.repeatEvery
        val unit = item.repeatUnit

        val next = if (every != null && unit != null) {
            calculateNextDue(now, every, unit)
        } else {
            null // não repete: fica concluído, sem novo vencimento
        }

        return item.copy(lastDoneAt = now, nextDueAt = next)
    }
}