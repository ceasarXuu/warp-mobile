package dev.warp.mobile.keyboard

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class TerminalActionType(val wireName: String) {
    SendRaw("sendRaw"),
    SendPrintable("sendPrintable"),
    SendModifiedKey("sendModifiedKey"),
    SendNavigation("sendNavigation"),
}

enum class NavigationKey(val wireName: String, val payload: String) {
    ArrowUp("arrowUp", "\u001b[A"),
    ArrowDown("arrowDown", "\u001b[B"),
    ArrowLeft("arrowLeft", "\u001b[D"),
    ArrowRight("arrowRight", "\u001b[C"),
    PageUp("pageUp", "\u001b[5~"),
    PageDown("pageDown", "\u001b[6~"),
    Home("home", "\u001b[H"),
    End("end", "\u001b[F"),
}

enum class ModifierKey(val wireName: String) {
    Ctrl("ctrl"),
    Alt("alt"),
    Shift("shift"),
}

enum class ModifierLatch {
    Inactive,
    OneShot,
    Locked,
}

data class TerminalModifierState(
    val ctrl: ModifierLatch = ModifierLatch.Inactive,
    val alt: ModifierLatch = ModifierLatch.Inactive,
    val shift: ModifierLatch = ModifierLatch.Inactive,
) {
    val isCtrlActive: Boolean get() = ctrl != ModifierLatch.Inactive
    val isAltActive: Boolean get() = alt != ModifierLatch.Inactive
    val isShiftActive: Boolean get() = shift != ModifierLatch.Inactive

    fun toggle(key: ModifierKey): TerminalModifierState {
        return when (key) {
            ModifierKey.Ctrl -> copy(ctrl = ctrl.next())
            ModifierKey.Alt -> copy(alt = alt.next())
            ModifierKey.Shift -> copy(shift = shift.next())
        }
    }

    fun consumeOneShot(): TerminalModifierState {
        return copy(
            ctrl = if (ctrl == ModifierLatch.OneShot) ModifierLatch.Inactive else ctrl,
            alt = if (alt == ModifierLatch.OneShot) ModifierLatch.Inactive else alt,
            shift = if (shift == ModifierLatch.OneShot) ModifierLatch.Inactive else shift,
        )
    }

    fun activeModifiers(): List<ModifierKey> {
        return buildList {
            if (isCtrlActive) add(ModifierKey.Ctrl)
            if (isAltActive) add(ModifierKey.Alt)
            if (isShiftActive) add(ModifierKey.Shift)
        }
    }

    fun toLogValue(): String {
        return "ctrl=${ctrl.name.lowercase()},alt=${alt.name.lowercase()},shift=${shift.name.lowercase()}"
    }

    private fun ModifierLatch.next(): ModifierLatch {
        return when (this) {
            ModifierLatch.Inactive -> ModifierLatch.OneShot
            ModifierLatch.OneShot -> ModifierLatch.Locked
            ModifierLatch.Locked -> ModifierLatch.Inactive
        }
    }
}

data class TerminalAction(
    val type: TerminalActionType,
    val payload: String,
    val keyId: String,
    val printableLength: Int = 0,
    val navigationKey: NavigationKey? = null,
    val modifiedKey: String? = null,
    val modifiers: List<ModifierKey> = emptyList(),
) {
    fun toBridgeJson(sequenceId: String): String {
        val payload = JSONObject()
        payload.put("kind", "TERMINAL_ACTION")
        payload.put("version", 1)
        payload.put("sequenceId", sequenceId)
        payload.put("source", "android_builtin_keyboard")
        payload.put(
            "action",
            JSONObject()
                .put("type", type.wireName)
                .put("payload", this.payload)
                .put("payloadHex", this.payload.toHexBytes())
                .put("keyId", keyId)
                .put("printableLength", printableLength)
                .put("navigationKey", navigationKey?.wireName)
                .put("modifiedKey", modifiedKey)
                .put("modifiers", JSONArray(modifiers.map { it.wireName })),
        )
        return payload.toString()
    }

    fun logFields(): Map<String, String> {
        return buildMap {
            put("action_type", type.wireName)
            put("key_id", keyId)
            put("printable_length", printableLength.toString())
            navigationKey?.let { put("navigation_key", it.wireName) }
            modifiedKey?.let { put("modified_key", it) }
            if (modifiers.isNotEmpty()) {
                put("modifiers", modifiers.joinToString("+") { it.wireName })
            }
        }
    }

    private fun String.toHexBytes(): String {
        return codeUnits().joinToString(" ") {
            it.toString(16).padStart(2, '0')
        }
    }

    private fun String.codeUnits(): List<Int> {
        return toCharArray().map { it.code }
    }

    companion object {
        fun escape(): TerminalAction = raw("esc", "\u001b")

        fun tab(): TerminalAction = raw("tab", "\t")

        fun enter(): TerminalAction = raw("enter", "\r")

        fun backspace(): TerminalAction = raw("backspace", "\u007f")

        fun delete(): TerminalAction = raw("delete", "\u001b[3~")

        fun raw(keyId: String, data: String): TerminalAction {
            return TerminalAction(
                type = TerminalActionType.SendRaw,
                payload = data,
                keyId = keyId,
            )
        }

        fun printable(
            value: String,
            keyId: String,
            modifierState: TerminalModifierState,
        ): TerminalAction {
            val resolved = resolvePrintable(value, modifierState)
            val modifiers = modifierState.activeModifiers()
            if (modifiers.isEmpty()) {
                return TerminalAction(
                    type = TerminalActionType.SendPrintable,
                    payload = resolved,
                    keyId = keyId,
                    printableLength = resolved.length,
                )
            }
            return modifiedKey(resolved, keyId, modifiers)
        }

        fun modifiedKey(
            key: String,
            keyId: String,
            modifiers: List<ModifierKey>,
        ): TerminalAction {
            return TerminalAction(
                type = TerminalActionType.SendModifiedKey,
                payload = modifiedKeyPayload(key, modifiers),
                keyId = keyId,
                modifiedKey = key,
                modifiers = modifiers,
            )
        }

        fun navigation(key: NavigationKey): TerminalAction {
            return TerminalAction(
                type = TerminalActionType.SendNavigation,
                payload = key.payload,
                keyId = key.wireName,
                navigationKey = key,
            )
        }

        fun resolvePrintable(
            value: String,
            modifierState: TerminalModifierState,
        ): String {
            if (!modifierState.isShiftActive) return value
            shiftedPrintableMap[value]?.let { return it }
            if (reverseShiftedPrintableMap.contains(value)) return value
            if (value.length == 1 && value[0].isLetter()) {
                return value.uppercase(Locale.US)
            }
            return value
        }

        fun modifiedKeyPayload(key: String, modifiers: List<ModifierKey>): String {
            val hasCtrl = ModifierKey.Ctrl in modifiers
            val hasAlt = ModifierKey.Alt in modifiers
            val hasShift = ModifierKey.Shift in modifiers

            if (key.length == 1) {
                val char = key[0]
                if (hasCtrl && !hasAlt && !hasShift) {
                    if (char in 'A'..'Z') return (char.code - 0x40).toChar().toString()
                    if (char in 'a'..'z') return (char.code - 0x60).toChar().toString()
                    when (key.uppercase(Locale.US)) {
                        "@" -> return "\u0000"
                        "[" -> return "\u001b"
                        "\\" -> return "\u001c"
                        "]" -> return "\u001d"
                        "^" -> return "\u001e"
                        "_" -> return "\u001f"
                        "?" -> return "\u007f"
                    }
                }
                if (hasAlt) {
                    val resolved = if (hasShift) key.uppercase(Locale.US) else key.lowercase(Locale.US)
                    return "\u001b$resolved"
                }
            }

            if (hasCtrl && key.equals("c", ignoreCase = true)) return "\u0003"
            if (hasCtrl && key.equals("d", ignoreCase = true)) return "\u0004"
            if (hasCtrl && key.equals("z", ignoreCase = true)) return "\u001a"
            if (hasCtrl && key.equals("l", ignoreCase = true)) return "\u000c"
            return key
        }
    }
}

private val shiftedPrintableMap = mapOf(
    "1" to "!",
    "2" to "@",
    "3" to "#",
    "4" to "\$",
    "5" to "%",
    "6" to "^",
    "7" to "&",
    "8" to "*",
    "9" to "(",
    "0" to ")",
    "-" to "_",
    "=" to "+",
    "[" to "{",
    "]" to "}",
    "\\" to "|",
    ";" to ":",
    "'" to "\"",
    "," to "<",
    "." to ">",
    "/" to "?",
    "`" to "~",
)

private val reverseShiftedPrintableMap = shiftedPrintableMap.values.toSet()
