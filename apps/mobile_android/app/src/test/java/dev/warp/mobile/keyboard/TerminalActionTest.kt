package dev.warp.mobile.keyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalActionTest {
    @Test
    fun modifierCyclesThroughOneShotLockedInactive() {
        val oneShot = TerminalModifierState().toggle(ModifierKey.Ctrl)
        val locked = oneShot.toggle(ModifierKey.Ctrl)
        val inactive = locked.toggle(ModifierKey.Ctrl)

        assertEquals(ModifierLatch.OneShot, oneShot.ctrl)
        assertEquals(ModifierLatch.Locked, locked.ctrl)
        assertEquals(ModifierLatch.Inactive, inactive.ctrl)
    }

    @Test
    fun consumeOneShotKeepsLockedModifiers() {
        val state = TerminalModifierState(
            ctrl = ModifierLatch.OneShot,
            alt = ModifierLatch.Locked,
            shift = ModifierLatch.OneShot,
        )

        val consumed = state.consumeOneShot()

        assertEquals(ModifierLatch.Inactive, consumed.ctrl)
        assertEquals(ModifierLatch.Locked, consumed.alt)
        assertEquals(ModifierLatch.Inactive, consumed.shift)
    }

    @Test
    fun buildsTerminalControlPayloads() {
        assertEquals("\u001b", TerminalAction.escape().payload)
        assertEquals("\t", TerminalAction.tab().payload)
        assertEquals("\r", TerminalAction.enter().payload)
        assertEquals("\u007f", TerminalAction.backspace().payload)
        assertEquals("\u001b[A", TerminalAction.navigation(NavigationKey.ArrowUp).payload)
        assertEquals("\u0003", TerminalAction.modifiedKey("c", "ctrl_c", listOf(ModifierKey.Ctrl)).payload)
        assertEquals("\u001ba", TerminalAction.modifiedKey("a", "alt_a", listOf(ModifierKey.Alt)).payload)
    }

    @Test
    fun shiftResolvesPrintableCharacters() {
        val shifted = TerminalModifierState(shift = ModifierLatch.OneShot)

        assertEquals("A", TerminalAction.resolvePrintable("a", shifted))
        assertEquals("!", TerminalAction.resolvePrintable("1", shifted))
        assertEquals("|", TerminalAction.resolvePrintable("\\", shifted))
    }

    @Test
    fun bridgeJsonIncludesSemanticActionWithoutLoggingPlaintextFields() {
        val action = TerminalAction.printable("a", "a", TerminalModifierState())
        val json = JSONObject(action.toBridgeJson("android-keyboard-7"))
        val actionJson = json.getJSONObject("action")

        assertEquals("TERMINAL_ACTION", json.getString("kind"))
        assertEquals("android-keyboard-7", json.getString("sequenceId"))
        assertEquals("sendPrintable", actionJson.getString("type"))
        assertEquals("a", actionJson.getString("payload"))
        assertEquals("61", actionJson.getString("payloadHex"))
        assertEquals("a", actionJson.getString("keyId"))
        assertEquals(0, actionJson.getJSONArray("modifiers").length())
        assertTrue(action.logFields().containsKey("printable_length"))
    }
}
