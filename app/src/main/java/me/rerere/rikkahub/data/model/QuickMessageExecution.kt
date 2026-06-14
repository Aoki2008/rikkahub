package me.rerere.rikkahub.data.model

import java.math.BigDecimal
import me.rerere.ai.core.MessageRole

data class QuickMessageExecutionPlan(
    val inputText: String,
    val sendMode: QuickMessageSendMode,
    val inputUpdated: Boolean = true,
    val unsupportedCommands: List<String> = emptyList(),
    val toastMessages: List<String> = emptyList(),
    val chatMessages: List<QuickMessageChatMessage> = emptyList(),
    val localVariables: Map<String, String> = emptyMap(),
    val globalVariables: Map<String, String> = emptyMap(),
    val variablesUpdated: Boolean = false,
    val expressionLabel: String? = null,
    val outputPipe: String = "",
    val aborted: Boolean = false,
)

data class QuickMessageChatMessage(
    val role: MessageRole,
    val text: String,
    val senderName: String? = null,
    val hiddenFromAi: Boolean = false,
)

enum class QuickMessageSendMode {
    NONE,
    NORMAL,
    WITHOUT_RESPONSE,
}

fun buildQuickMessageExecutionPlan(
    quickMessage: QuickMessage,
    currentInput: String,
    localVariables: Map<String, String> = emptyMap(),
    globalVariables: Map<String, String> = emptyMap(),
): QuickMessageExecutionPlan {
    val input = composeQuickMessageInput(quickMessage, currentInput)

    if (quickMessage.sendImmediately && input.trimStart().startsWith("/")) {
        executeQuickMessageSlashCommands(
            input = input,
            currentInput = currentInput,
            localVariables = localVariables,
            globalVariables = globalVariables,
        )?.let { return it }
        return QuickMessageExecutionPlan(
            inputText = input,
            sendMode = QuickMessageSendMode.NONE,
            inputUpdated = true,
            unsupportedCommands = listOf(input.trim()),
            localVariables = localVariables,
            globalVariables = globalVariables,
        )
    }

    return QuickMessageExecutionPlan(
        inputText = input,
        sendMode = if (quickMessage.sendImmediately) QuickMessageSendMode.NORMAL else QuickMessageSendMode.NONE,
        localVariables = localVariables,
        globalVariables = globalVariables,
    )
}

private fun composeQuickMessageInput(
    quickMessage: QuickMessage,
    currentInput: String,
): String {
    val message = quickMessage.content.trim()
    val input = currentInput.trim()

    return when {
        quickMessage.injectInput && input.isNotEmpty() && quickMessage.placeBeforeInput ->
            joinQuickReplyInput(message, input)

        quickMessage.injectInput && input.isNotEmpty() ->
            joinQuickReplyInput(input, message)

        !quickMessage.sendImmediately && input.isNotEmpty() ->
            currentInput + quickMessage.content

        else -> message
    }
}

private fun joinQuickReplyInput(first: String, second: String): String =
    listOf(first.trim(), second.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")

private fun executeQuickMessageSlashCommands(
    input: String,
    currentInput: String,
    localVariables: Map<String, String>,
    globalVariables: Map<String, String>,
): QuickMessageExecutionPlan? {
    val state = QuickMessageVariableState(localVariables, globalVariables)
    var inputText = ""
    var pipe = ""
    var sendMode = QuickMessageSendMode.NONE
    var hasCompatibleCommand = false
    var hasInputUpdate = false
    var aborted = false
    var expressionLabel: String? = null
    val unsupported = mutableListOf<String>()
    val toastMessages = mutableListOf<String>()
    val chatMessages = mutableListOf<QuickMessageChatMessage>()

    commandLoop@ for (commandText in splitSlashPipeline(input)) {
        val command = parseSlashCommand(commandText)
        if (command == null) {
            if (commandText.isNotBlank()) unsupported += commandText.trim()
            continue
        }

        val arguments = if (command.name.equals("if", ignoreCase = true)) {
            parseIfSlashArguments(command.args, pipe, state)
        } else {
            parseSlashArguments(command.args, pipe, state)
        }
        val args = arguments.unnamed
        when (command.name.lowercase()) {
            "pass" -> {
                hasCompatibleCommand = true
                pipe = resolveSlashArgument(args, pipe)
            }

            "setinput", "input" -> {
                hasCompatibleCommand = true
                hasInputUpdate = true
                inputText = resolveSlashArgument(args, pipe)
                pipe = inputText
                sendMode = QuickMessageSendMode.NONE
            }

            "send" -> {
                hasCompatibleCommand = true
                hasInputUpdate = true
                inputText = resolveSlashArgument(args, pipe)
                pipe = inputText
                sendMode = QuickMessageSendMode.WITHOUT_RESPONSE
            }

            "trigger", "gen", "generate" -> {
                hasCompatibleCommand = true
                sendMode = QuickMessageSendMode.NORMAL
            }

            "echo" -> {
                hasCompatibleCommand = true
                pipe = resolveSlashArgument(args, pipe)
                if (pipe.isNotBlank()) {
                    toastMessages += pipe
                }
            }

            "return" -> {
                hasCompatibleCommand = true
                pipe = resolveSlashArgument(args, pipe)
            }

            "abort" -> {
                hasCompatibleCommand = true
                aborted = true
                break@commandLoop
            }

            "if" -> {
                hasCompatibleCommand = true
                val branch = resolveIfBranch(arguments, state)
                if (branch.isNotBlank()) {
                    val branchPlan = executeQuickMessageSlashCommands(
                        input = branch,
                        currentInput = if (hasInputUpdate) inputText else currentInput,
                        localVariables = state.localVariables(),
                        globalVariables = state.globalVariables(),
                    )
                    if (branchPlan == null) {
                        unsupported += "/if"
                    } else {
                        if (branchPlan.inputUpdated) {
                            hasInputUpdate = true
                            inputText = branchPlan.inputText
                        }
                        if (branchPlan.sendMode != QuickMessageSendMode.NONE) {
                            sendMode = branchPlan.sendMode
                        }
                        unsupported += branchPlan.unsupportedCommands
                        toastMessages += branchPlan.toastMessages
                        chatMessages += branchPlan.chatMessages
                        branchPlan.expressionLabel?.let { expressionLabel = it }
                        state.replaceWith(branchPlan.localVariables, branchPlan.globalVariables)
                        pipe = branchPlan.outputPipe
                        if (branchPlan.aborted) {
                            aborted = true
                            break@commandLoop
                        }
                    }
                } else {
                    pipe = ""
                }
            }

            "getvar" -> {
                hasCompatibleCommand = true
                pipe = state.getLocalOrGlobal(resolveVariableName(arguments, args))
            }

            "setvar" -> {
                hasCompatibleCommand = true
                val (name, value) = resolveVariableNameAndValue(arguments, args, pipe)
                if (name.isNotBlank()) {
                    pipe = state.setLocal(name, value)
                }
            }

            "addvar" -> {
                hasCompatibleCommand = true
                val (name, increment) = resolveVariableNameAndValue(arguments, args, pipe)
                if (name.isNotBlank()) {
                    pipe = state.addLocal(name, increment)
                }
            }

            "incvar" -> {
                hasCompatibleCommand = true
                val name = resolveVariableName(arguments, args)
                if (name.isNotBlank()) {
                    pipe = state.addLocal(name, "1")
                }
            }

            "decvar" -> {
                hasCompatibleCommand = true
                val name = resolveVariableName(arguments, args)
                if (name.isNotBlank()) {
                    pipe = state.addLocal(name, "-1")
                }
            }

            "flushvar" -> {
                hasCompatibleCommand = true
                val name = resolveVariableName(arguments, args)
                if (name.isNotBlank()) {
                    state.flushLocal(name)
                    pipe = ""
                }
            }

            "getglobalvar" -> {
                hasCompatibleCommand = true
                pipe = state.getGlobal(resolveVariableName(arguments, args))
            }

            "setglobalvar" -> {
                hasCompatibleCommand = true
                val (name, value) = resolveVariableNameAndValue(arguments, args, pipe)
                if (name.isNotBlank()) {
                    pipe = state.setGlobal(name, value)
                }
            }

            "addglobalvar" -> {
                hasCompatibleCommand = true
                val (name, increment) = resolveVariableNameAndValue(arguments, args, pipe)
                if (name.isNotBlank()) {
                    pipe = state.addGlobal(name, increment)
                }
            }

            "incglobalvar" -> {
                hasCompatibleCommand = true
                val name = resolveVariableName(arguments, args)
                if (name.isNotBlank()) {
                    pipe = state.addGlobal(name, "1")
                }
            }

            "decglobalvar" -> {
                hasCompatibleCommand = true
                val name = resolveVariableName(arguments, args)
                if (name.isNotBlank()) {
                    pipe = state.addGlobal(name, "-1")
                }
            }

            "flushglobalvar" -> {
                hasCompatibleCommand = true
                val name = resolveVariableName(arguments, args)
                if (name.isNotBlank()) {
                    state.flushGlobal(name)
                    pipe = ""
                }
            }

            "sys" -> {
                hasCompatibleCommand = true
                val message = resolveSlashArgument(args, pipe)
                if (message.isNotBlank()) {
                    chatMessages += QuickMessageChatMessage(
                        role = MessageRole.SYSTEM,
                        text = message,
                    )
                    pipe = message
                }
            }

            "comment" -> {
                hasCompatibleCommand = true
                val message = resolveSlashArgument(args, pipe)
                if (message.isNotBlank()) {
                    chatMessages += QuickMessageChatMessage(
                        role = MessageRole.SYSTEM,
                        text = message,
                        hiddenFromAi = true,
                    )
                    pipe = message
                }
            }

            "sendas" -> {
                hasCompatibleCommand = true
                val message = resolveSlashArgument(args, pipe)
                if (message.isNotBlank()) {
                    chatMessages += QuickMessageChatMessage(
                        role = MessageRole.ASSISTANT,
                        text = message,
                        senderName = arguments.named["name"]
                            ?: arguments.named["character"]
                            ?: arguments.named["char"],
                    )
                    pipe = message
                }
            }

            "expression-set" -> {
                hasCompatibleCommand = true
                val label = resolveExpressionLabel(arguments, args, pipe)
                if (label.isNotBlank()) {
                    expressionLabel = label
                    pipe = label
                } else {
                    unsupported += "/${command.name}"
                }
            }

            "/", "#", "breakpoint", "parser-flag" -> {
                hasCompatibleCommand = true
            }

            else -> unsupported += "/${command.name}"
        }
    }

    if (!hasCompatibleCommand && unsupported.isNotEmpty()) {
        return null
    }

    return QuickMessageExecutionPlan(
        inputText = if (hasInputUpdate) inputText else currentInput,
        sendMode = sendMode,
        inputUpdated = hasInputUpdate,
        unsupportedCommands = unsupported,
        toastMessages = toastMessages,
        chatMessages = chatMessages,
        localVariables = state.localVariables(),
        globalVariables = state.globalVariables(),
        variablesUpdated = state.variablesUpdated(),
        expressionLabel = expressionLabel,
        outputPipe = pipe,
        aborted = aborted,
    )
}

private class QuickMessageVariableState(
    localVariables: Map<String, String>,
    globalVariables: Map<String, String>,
) {
    private val initialLocalVariables = localVariables.normalizedVariableMap()
    private val initialGlobalVariables = globalVariables.normalizedVariableMap()
    private val local = initialLocalVariables.toMutableMap()
    private val global = initialGlobalVariables.toMutableMap()

    fun localVariables(): Map<String, String> = local.toMap()

    fun globalVariables(): Map<String, String> = global.toMap()

    fun variablesUpdated(): Boolean =
        local != initialLocalVariables || global != initialGlobalVariables

    fun getLocalOrGlobal(name: String): String =
        local[normalizeVariableName(name)] ?: getGlobal(name)

    fun getGlobal(name: String): String =
        global[normalizeVariableName(name)].orEmpty()

    fun getLocalOrGlobalOrNull(name: String): String? {
        val key = normalizeVariableName(name)
        return when {
            local.containsKey(key) -> local[key]
            global.containsKey(key) -> global[key]
            else -> null
        }
    }

    fun replaceWith(
        localVariables: Map<String, String>,
        globalVariables: Map<String, String>,
    ) {
        local.clear()
        local.putAll(localVariables.normalizedVariableMap())
        global.clear()
        global.putAll(globalVariables.normalizedVariableMap())
    }

    fun setLocal(name: String, value: String): String =
        set(local, name, value)

    fun setGlobal(name: String, value: String): String =
        set(global, name, value)

    fun addLocal(name: String, increment: String): String =
        add(local, name, increment)

    fun addGlobal(name: String, increment: String): String =
        add(global, name, increment)

    fun flushLocal(name: String) {
        local.remove(normalizeVariableName(name))
    }

    fun flushGlobal(name: String) {
        global.remove(normalizeVariableName(name))
    }

    fun expandMacros(value: String, pipe: String): String =
        value.replacePipeMacro(pipe).replace(variableMacroRegex) { match ->
            val macro = match.groupValues[1].lowercase()
            val parts = match.groupValues[2].split("::", limit = 2)
            val name = parts.getOrNull(0).orEmpty()
            val argument = parts.getOrNull(1).orEmpty()
            when (macro) {
                "var", "getvar" -> getLocalOrGlobal(name)
                "getglobalvar" -> getGlobal(name)
                "incvar" -> addLocal(name, "1")
                "decvar" -> addLocal(name, "-1")
                "incglobalvar" -> addGlobal(name, "1")
                "decglobalvar" -> addGlobal(name, "-1")
                "setvar" -> {
                    setLocal(name, argument)
                    ""
                }
                "setglobalvar" -> {
                    setGlobal(name, argument)
                    ""
                }
                "addvar" -> {
                    addLocal(name, argument)
                    ""
                }
                "addglobalvar" -> {
                    addGlobal(name, argument)
                    ""
                }
                else -> match.value
            }
        }

    private fun set(target: MutableMap<String, String>, name: String, value: String): String {
        val key = normalizeVariableName(name)
        if (key.isBlank()) return ""
        target[key] = value
        return value
    }

    private fun add(target: MutableMap<String, String>, name: String, increment: String): String {
        val key = normalizeVariableName(name)
        if (key.isBlank()) return ""
        val current = target[key].orEmpty()
        val next = addVariableValues(current, increment)
        target[key] = next
        return next
    }
}

private fun Map<String, String>.normalizedVariableMap(): Map<String, String> =
    entries
        .mapNotNull { (key, value) ->
            normalizeVariableName(key).takeIf { it.isNotBlank() }?.let { it to value }
        }
        .toMap()

private fun normalizeVariableName(name: String): String =
    name.trim()

private fun resolveVariableName(arguments: SlashArguments, args: String): String =
    arguments.named["key"]
        ?: arguments.named["name"]
        ?: splitFirstSlashArgument(args).first

private fun resolveVariableNameAndValue(
    arguments: SlashArguments,
    args: String,
    pipe: String,
): Pair<String, String> {
    val namedKey = arguments.named["key"] ?: arguments.named["name"]
    val namedValue = arguments.named["value"] ?: arguments.named["increment"]
    if (namedKey != null) {
        return namedKey to (namedValue ?: resolveSlashArgument(args, pipe))
    }

    val (name, rest) = splitFirstSlashArgument(args)
    return name to (namedValue ?: resolveSlashArgument(rest, pipe))
}

private fun resolveExpressionLabel(
    arguments: SlashArguments,
    args: String,
    pipe: String,
): String =
    arguments.named["name"]
        ?: arguments.named["expression"]
        ?: arguments.named["sprite"]
        ?: arguments.named["label"]
        ?: resolveSlashArgument(args, pipe)

private fun splitFirstSlashArgument(value: String): Pair<String, String> {
    val trimmed = value.trimStart()
    if (trimmed.isBlank()) return "" to ""

    val first = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var index = 0

    while (index < trimmed.length) {
        val char = trimmed[index]
        when {
            escaped -> {
                first.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            quote != null -> {
                if (char == quote) {
                    quote = null
                } else {
                    first.append(char)
                }
            }
            char == '"' || char == '\'' -> quote = char
            char.isWhitespace() -> {
                return first.toString() to trimmed.substring(index + 1).trimStart()
            }
            else -> first.append(char)
        }
        index++
    }

    return first.toString() to ""
}

private fun addVariableValues(current: String, increment: String): String {
    val left = current.ifBlank { "0" }.toBigDecimalOrNull()
    val right = increment.ifBlank { "0" }.toBigDecimalOrNull()
    return if (left != null && right != null) {
        (left + right).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }
    } else {
        current + increment
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    runCatching { BigDecimal(trim()) }.getOrNull()

private data class ParsedSlashCommand(
    val name: String,
    val args: String,
)

private fun parseSlashCommand(commandText: String): ParsedSlashCommand? {
    val trimmed = commandText.trim()
    if (!trimmed.startsWith("/")) return null
    val withoutSlash = trimmed.drop(1).trimStart()
    if (withoutSlash.isEmpty()) return null

    val nameEnd = withoutSlash.indexOfFirst { it.isWhitespace() }
    return if (nameEnd == -1) {
        ParsedSlashCommand(name = withoutSlash, args = "")
    } else {
        ParsedSlashCommand(
            name = withoutSlash.substring(0, nameEnd),
            args = withoutSlash.substring(nameEnd + 1),
        )
    }
}

private fun splitSlashPipeline(input: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var macroDepth = 0
    var closureDepth = 0
    var quote: Char? = null
    var escaped = false
    var index = 0

    while (index < input.length) {
        val char = input[index]
        val next = input.getOrNull(index + 1)

        when {
            escaped -> {
                current.append(char)
                escaped = false
            }

            char == '\\' -> {
                current.append(char)
                escaped = true
            }

            quote != null -> {
                current.append(char)
                if (char == quote) quote = null
            }

            char == '"' || char == '\'' -> {
                current.append(char)
                quote = char
            }

            char == '{' && next == '{' -> {
                current.append("{{")
                macroDepth++
                index++
            }

            char == '{' && next == ':' -> {
                current.append("{:")
                closureDepth++
                index++
            }

            char == '}' && next == '}' && macroDepth > 0 -> {
                current.append("}}")
                macroDepth--
                index++
            }

            char == ':' && next == '}' && closureDepth > 0 -> {
                current.append(":}")
                closureDepth--
                index++
            }

            char == '|' && macroDepth == 0 && closureDepth == 0 -> {
                parts += current.toString()
                current.clear()
            }

            else -> current.append(char)
        }

        index++
    }

    parts += current.toString()
    return parts
}

private data class SlashArguments(
    val named: Map<String, String>,
    val unnamed: String,
)

private fun parseSlashArguments(
    args: String,
    pipe: String,
    state: QuickMessageVariableState,
): SlashArguments {
    var remaining = args.trimStart()
    val named = mutableMapOf<String, String>()

    while (true) {
        val match = leadingNamedArgumentRegex.find(remaining) ?: break
        val key = match.groupValues[1].lowercase()
        val value = match.groupValues
            .drop(2)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
            .unescapeSlashArgument()
            .let { state.expandMacros(it, pipe) }
        named[key] = value
        remaining = remaining.substring(match.range.last + 1).trimStart()
    }

    return SlashArguments(
        named = named,
        unnamed = state.expandMacros(remaining, pipe).trim(),
    )
}

private fun parseIfSlashArguments(
    args: String,
    pipe: String,
    state: QuickMessageVariableState,
): SlashArguments {
    var index = 0
    val named = mutableMapOf<String, String>()

    while (true) {
        index = args.skipWhitespace(index)
        val keyStart = index
        if (keyStart >= args.length || !args[keyStart].isLetterOrUnderscore()) break

        index++
        while (index < args.length && args[index].isSlashIdentifierPart()) {
            index++
        }

        val key = args.substring(keyStart, index)
        if (index >= args.length || args[index] != '=') {
            index = keyStart
            break
        }
        index++

        val value = args.readSlashArgumentValue(index)
        if (value == null) {
            index = keyStart
            break
        }
        named[key.lowercase()] = if (value.isClosure) {
            value.value.trim()
        } else {
            state.expandMacros(value.value, pipe).trim()
        }
        index = value.nextIndex
    }

    val unnamedRaw = args.substring(index).trim()
    val unnamed = args.readIfUnnamedArgument(index)
        ?.let { value ->
            if (value.isClosure) value.value.trim() else state.expandMacros(value.value, pipe).trim()
        }
        ?: state.expandMacros(unnamedRaw, pipe).trim()

    return SlashArguments(named = named, unnamed = unnamed)
}

private data class SlashArgumentValue(
    val value: String,
    val nextIndex: Int,
    val isClosure: Boolean,
)

private fun resolveIfBranch(
    arguments: SlashArguments,
    state: QuickMessageVariableState,
): String {
    val rule = arguments.named["rule"].orEmpty().ifBlank { "eq" }.lowercase()
    val left = resolveIfOperand(arguments.named["left"].orEmpty(), state)
    val right = resolveIfOperand(arguments.named["right"].orEmpty(), state)
    val matched = when (rule) {
        "eq", "equals", "==" -> left == right
        "neq", "ne", "!=" -> left != right
        "lt", "<" -> compareIfOperands(left, right) < 0
        "gt", ">" -> compareIfOperands(left, right) > 0
        "lte", "le", "<=" -> compareIfOperands(left, right) <= 0
        "gte", "ge", ">=" -> compareIfOperands(left, right) >= 0
        "not", "!" -> !left.isStScriptTruthy()
        "in" -> left.contains(right, ignoreCase = true)
        "nin" -> !left.contains(right, ignoreCase = true)
        else -> false
    }

    return if (matched) arguments.unnamed else arguments.named["else"].orEmpty()
}

private fun resolveIfOperand(
    value: String,
    state: QuickMessageVariableState,
): String {
    val trimmed = value.trim()
    if (trimmed.toBigDecimalOrNull() != null) return trimmed
    return state.getLocalOrGlobalOrNull(trimmed) ?: trimmed
}

private fun compareIfOperands(left: String, right: String): Int {
    val leftNumber = left.toBigDecimalOrNull()
    val rightNumber = right.toBigDecimalOrNull()
    return if (leftNumber != null && rightNumber != null) {
        leftNumber.compareTo(rightNumber)
    } else {
        left.compareTo(right)
    }
}

private fun String.isStScriptTruthy(): Boolean =
    trim().lowercase().let { value ->
        value.isNotEmpty() && value !in setOf("0", "false", "off", "no", "null")
    }

private fun String.readIfUnnamedArgument(startIndex: Int): SlashArgumentValue? {
    val index = skipWhitespace(startIndex)
    if (index >= length) return null
    return readSlashArgumentValue(index)
        ?: SlashArgumentValue(
            value = substring(index).trim(),
            nextIndex = length,
            isClosure = false,
        )
}

private fun String.readSlashArgumentValue(startIndex: Int): SlashArgumentValue? {
    val index = skipWhitespace(startIndex)
    if (index >= length) return null

    if (getOrNull(index) == '{' && getOrNull(index + 1) == ':') {
        return readSlashClosureValue(index)
    }

    val quote = this[index]
    if (quote == '"' || quote == '\'') {
        val value = StringBuilder()
        var escaped = false
        var cursor = index + 1
        while (cursor < length) {
            val char = this[cursor]
            when {
                escaped -> {
                    value.append(char)
                    escaped = false
                }

                char == '\\' -> escaped = true
                char == quote -> {
                    return SlashArgumentValue(
                        value = value.toString(),
                        nextIndex = cursor + 1,
                        isClosure = false,
                    )
                }

                else -> value.append(char)
            }
            cursor++
        }
        return SlashArgumentValue(value = value.toString(), nextIndex = length, isClosure = false)
    }

    var cursor = index
    while (cursor < length && !this[cursor].isWhitespace()) {
        cursor++
    }
    return SlashArgumentValue(
        value = substring(index, cursor).unescapeSlashArgument(),
        nextIndex = cursor,
        isClosure = false,
    )
}

private fun String.readSlashClosureValue(startIndex: Int): SlashArgumentValue? {
    var depth = 1
    var quote: Char? = null
    var escaped = false
    var cursor = startIndex + 2
    val valueStart = cursor

    while (cursor < length) {
        val char = this[cursor]
        val next = getOrNull(cursor + 1)
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            quote != null -> {
                if (char == quote) quote = null
            }

            char == '"' || char == '\'' -> quote = char
            char == '{' && next == ':' -> {
                depth++
                cursor++
            }

            char == ':' && next == '}' -> {
                depth--
                if (depth == 0) {
                    return SlashArgumentValue(
                        value = substring(valueStart, cursor),
                        nextIndex = cursor + 2,
                        isClosure = true,
                    )
                }
                cursor++
            }
        }
        cursor++
    }

    return null
}

private fun String.skipWhitespace(startIndex: Int): Int {
    var index = startIndex
    while (index < length && this[index].isWhitespace()) {
        index++
    }
    return index
}

private fun Char.isLetterOrUnderscore(): Boolean =
    isLetter() || this == '_'

private fun Char.isSlashIdentifierPart(): Boolean =
    isLetterOrDigit() || this == '_' || this == '-'

private val leadingNamedArgumentRegex =
    Regex("""^([A-Za-z_][A-Za-z0-9_-]*)=(?:"((?:\\.|[^"])*)"|'((?:\\.|[^'])*)'|(\S+))\s*""")

private val variableMacroRegex =
    Regex("""\{\{(var|getvar|getglobalvar|setvar|setglobalvar|addvar|addglobalvar|incvar|decvar|incglobalvar|decglobalvar)::([^}]*)}}""")

private fun String.replacePipeMacro(pipe: String): String =
    replace("{{pipe}}", pipe)

private fun String.unescapeSlashArgument(): String =
    replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\\\", "\\")

private fun unquoteSlashArgument(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length < 2) return trimmed
    val first = trimmed.first()
    val last = trimmed.last()
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed
            .substring(1, trimmed.lastIndex)
            .unescapeSlashArgument()
    }
    return trimmed
}

private fun resolveSlashArgument(args: String, pipe: String): String =
    if (args.isBlank()) pipe else unquoteSlashArgument(args)
