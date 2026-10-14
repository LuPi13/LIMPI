package com.github.lupi13.limpi.commands

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WebHelpTest {
    @Test fun `each system links to its own page and its section of the admin page`() {
        assertEquals(listOf("https://example.org/LIMPI/chest/"), WebHelp.links("https://example.org/LIMPI/", false, "chest").map { it.url })
        assertEquals(listOf("https://example.org/LIMPI/financial/", "https://example.org/LIMPI/admin/#financial"),
            WebHelp.links(" https://example.org/LIMPI ", true, "financial").map { it.url })
    }
    @Test fun `limpi help links to the front page and the whole admin page`() {
        assertEquals(listOf("https://example.org/LIMPI/", "https://example.org/LIMPI/admin/"), WebHelp.links("https://example.org/LIMPI", true, null).map { it.url })
    }
    @Test fun `blank or missing address hides the links`() {
        assertTrue(WebHelp.links("", true, "ability").isEmpty()); assertTrue(WebHelp.links("  ", false, null).isEmpty())
        assertTrue(WebHelp.links(null, true, "notice").isEmpty())
    }
}
