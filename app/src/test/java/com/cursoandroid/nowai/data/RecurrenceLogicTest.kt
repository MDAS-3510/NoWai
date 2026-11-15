package com.cursoandroid.nowai.data

import org.junit.Assert.*
import org.junit.Test

class RecurrenceLogicTest {

    @Test
    fun `concluir item recorrente define proximo vencimento a partir de agora`() {
        val item = Item(
            title = "Trocar lençóis",
            type = ItemType.TASK,
            repeatEvery = 2,
            repeatUnit = RepeatUnit.WEEKS
        )
        val now = 1_700_000_000_000L

        val done = RecurrenceLogic.complete(item, now)

        assertEquals(now, done.lastDoneAt)
        assertNotNull(done.nextDueAt)
        assertTrue(done.nextDueAt!! > now)
    }

    @Test
    fun `concluir item sem repeticao nao gera proximo vencimento`() {
        val item = Item(title = "Pagar boleto", type = ItemType.TASK)

        val done = RecurrenceLogic.complete(item)

        assertNull(done.nextDueAt)
    }
}