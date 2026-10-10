package com.github.lupi13.limpi.economy

import com.github.lupi13.limpi.storage.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class BankServiceTest {
    @TempDir lateinit var directory: Path
    private val sender = UUID.randomUUID()
    private val recipient = UUID.randomUUID()
    private fun file() = directory.resolve("limpi.sqlite3")
    private fun fixture(store: StateStore, from: Long = 1000, to: Long = 50): Pair<CachedPlayerRepository, BankService> {
        val profiles = CachedPlayerRepository(SqliteProfilePersistence(store))
        profiles.ensure(sender, "Sender", from)
        profiles.ensure(recipient, "Recipient", to)
        return profiles to BankService(store, profiles)
    }

    @Test fun `both sides fee and receipt are committed atomically`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            val id = UUID.randomUUID()
            val result = bank.transfer(id, sender, recipient, 100)
            assertEquals(5L, result.fee)
            assertEquals(895L, profiles.profile(sender).balance)
            assertEquals(150L, profiles.profile(recipient).balance)
            assertEquals(1045L, result.after.values.sum())
            assertNotNull(store.entry(id))
        }
    }

    @Test fun `retries and restart reuse the receipt without another debit`() {
        val id = UUID.randomUUID()
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            assertFalse(bank.transfer(id, sender, recipient, 100).replayed)
            assertTrue(bank.transfer(id, sender, recipient, 100).replayed)
            assertEquals(895L, profiles.profile(sender).balance)
        }
        SqliteStateStore(file()).use { store ->
            val profiles = CachedPlayerRepository(SqliteProfilePersistence(store))
            assertTrue(BankService(store, profiles).transfer(id, sender, recipient, 100).replayed)
            assertEquals(895L, profiles.profile(sender).balance)
            assertEquals(150L, profiles.profile(recipient).balance)
        }
    }

    @Test fun `altered arguments with the same ID are rejected`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            val id = UUID.randomUUID()
            bank.transfer(id, sender, recipient, 100)
            assertThrows(TransactionIdentityConflict::class.java) { bank.transfer(id, sender, recipient, 200) }
            assertThrows(TransactionIdentityConflict::class.java) { bank.transfer(id, recipient, sender, 100) }
            assertEquals(895L, profiles.profile(sender).balance)
        }
    }

    @Test fun `failed commit preserves sender recipient and cache and remains retryable`() {
        var fail = false
        SqliteStateStore(file(), beforeCommit = { if (fail) throw IllegalStateException("fault") }).use { store ->
            val (profiles, bank) = fixture(store)
            val id = UUID.randomUUID()
            fail = true
            assertThrows(IllegalStateException::class.java) { bank.transfer(id, sender, recipient, 100) }
            assertEquals(1000L, profiles.profile(sender).balance)
            assertEquals(50L, profiles.profile(recipient).balance)
            assertNull(store.entry(id))
            fail = false
            assertFalse(bank.transfer(id, sender, recipient, 100).replayed)
            assertEquals(895L, profiles.profile(sender).balance)
        }
    }

    @Test fun `invalid or unaffordable transfers cannot mutate either account`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            for (value in listOf(0L, -1L, 1000L)) {
                assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, recipient, value) }
            }
            assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, sender, 1) }
            assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, UUID.randomUUID(), 1) }
            assertEquals(1000L, profiles.profile(sender).balance)
            assertEquals(50L, profiles.profile(recipient).balance)
        }
    }

    @Test fun `receiver overflow fails before sender debit`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store, to = Long.MAX_VALUE)
            val failure = assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, recipient, 1) }
            assertEquals(BankFailure.OVERFLOW, failure.reason)
            assertEquals(1000L, profiles.profile(sender).balance)
            assertEquals(Long.MAX_VALUE, profiles.profile(recipient).balance)
        }
    }

    @Test fun `Long maximum plus fee cannot wrap to an affordable negative cost`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store, from = Long.MAX_VALUE)
            assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, recipient, Long.MAX_VALUE) }
            assertEquals(Long.MAX_VALUE, profiles.profile(sender).balance)
        }
    }

    @Test fun `old ability edits preserve newly committed bank balances`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            val edit = profiles.snapshot(sender)
            bank.transfer(UUID.randomUUID(), sender, recipient, 100)
            edit.set("ability", "web_shooter")
            profiles.save(sender, edit)
            assertEquals(895L, profiles.profile(sender).balance)
            assertEquals("web_shooter", profiles.abilityCode(sender))
        }
    }

    @Test fun `administrator adjustments use checked signed arithmetic and never allow overdrafts`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            bank.adjust(UUID.randomUUID(), "CONSOLE", sender, -100)
            assertEquals(900L, profiles.profile(sender).balance)
            assertThrows(BankException::class.java) { bank.adjust(UUID.randomUUID(), "CONSOLE", sender, -901) }
            assertThrows(BankException::class.java) { bank.adjust(UUID.randomUUID(), "CONSOLE", sender, Long.MIN_VALUE) }
            assertThrows(BankException::class.java) { bank.adjust(UUID.randomUUID(), "CONSOLE", sender, Long.MAX_VALUE) }
            assertEquals(900L, profiles.profile(sender).balance)
        }
    }

    @Test fun `explicit administrator set can repair a negative balance with an audit record`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            store.atomic { it.put(SqliteProfilePersistence.key(sender), "name: Sender\nmoney: -100\n") }
            profiles.invalidate(listOf(sender))
            assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, recipient, 1) }
            val receipt = bank.set(UUID.randomUUID(), "CONSOLE", sender, 0)
            assertEquals(-100L, receipt.before.getValue(sender))
            assertEquals(0L, profiles.profile(sender).balance)
            assertEquals("CONSOLE", receipt.actor)
        }
    }

    @Test fun `malformed balance is exposed as unknown instead of zero`() {
        SqliteStateStore(file()).use { store ->
            val (profiles, bank) = fixture(store)
            store.atomic { it.put(SqliteProfilePersistence.key(sender), "name: Sender\nmoney: 3.5\n") }
            profiles.invalidate(listOf(sender))
            assertNull(profiles.profile(sender).balance)
            assertEquals("Sender", profiles.profile(sender).name)
            assertThrows(BankException::class.java) { bank.transfer(UUID.randomUUID(), sender, recipient, 1) }
            assertEquals("name: Sender\nmoney: 3.5\n", store.get(SqliteProfilePersistence.key(sender)))
        }
    }

    @Test fun `fingerprints preserve field boundaries Unicode and action identity`() {
        assertNotEquals(BankService.fingerprint("a|b", "c"), BankService.fingerprint("a", "b|c"))
        assertNotEquals(BankService.fingerprint("SET", "한글"), BankService.fingerprint("ADJUST", "한글"))
        assertEquals(64, BankService.fingerprint("한글", "a\nb").length)
    }
}
