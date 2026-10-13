package com.github.lupi13.limpi.commands

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WebHelpTest {
    @Test fun `players get the command page and admins also get the admin page`() {
        assertEquals(listOf("https://example.org/LIMPI/commands/"), WebHelp.links("https://example.org/LIMPI/", false).map { it.url })
        assertEquals(listOf("https://example.org/LIMPI/commands/", "https://example.org/LIMPI/admin/"),
            WebHelp.links(" https://example.org/LIMPI ", true).map { it.url })
    }
    @Test fun `blank or missing address hides the links`() {
        assertTrue(WebHelp.links("", true).isEmpty()); assertTrue(WebHelp.links("  ", false).isEmpty()); assertTrue(WebHelp.links(null, true).isEmpty())
    }
}
