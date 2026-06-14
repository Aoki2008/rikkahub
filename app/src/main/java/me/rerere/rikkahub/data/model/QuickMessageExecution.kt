package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole

data class QuickMessageExecutionPlan(
    val inputText: String,
    val sendMode: QuickMessageSendMode,
    val inputUpdated: Boolean = true,
    val unsupportedCommands: List<String> = emptyList(),
    val toastMessages: List<String> = emptyList(),
    val chatMessages: List<QuickMessageChatMessage> = emptyList(),
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
): QuickMessageExecutionPlan {
    val input = composeQuickMessageInput(quickMessage, currentInput)

    if (quickMessage.sendImmediately && input.trimStart().startsWith("/")) {
        executeQuickMessageSlashCommands(input, currentInput)?.let { return it }
        return QuickMessageExecutionPlan(
            inputText = input,
            sendMode = QuickMessageSendMode.NONE,
            inputUpdated = true,
            unsupportedCommands = listOf(input.trim()),
        )
    }

    return QuickMessageExecutionPlan(
        inputText = input,
        sendMode = if (quickMessage.sendImmediately) QuickMessageSendMode.NORMAL else QuickMessageSendMode.NONE,
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

private fun executeQuickMessageSlashCommands(input: String, currentInput: String): QuickMessageExecutionPlan? {
    var inputText = ""
    var pipe = ""
    var sendMode = QuickMessageSendMode.NONE
    var hasCompatibleCommand = false
    var hasInputUpdate = false
    val unsupported = mutableListOf<String>()
    val toastMessages = mutableListOf<String>()
    val chatMessages = mutableListOf<QuickMessageChatMessage>()

    for (commandText in splitSlashPipeline(input)) {
        val command = parseSlashCommand(commandText)
        if (command == null) {
            if (commandText.isNotBlank()) unsupported += commandText.trim()
            continue
        }

        val arguments = parseSlashArguments(command.args, pipe)
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
    )
}

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

            char == '}' && next == '}' && macroDepth > 0 -> {
                current.append("}}")
                macroDepth--
                index++
            }

            char == '|' && macroDepth == 0 -> {
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

private fun parseSlashArguments(args: String, pipe: String): SlashArguments {
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
            .replacePipeMacro(pipe)
        named[key] = value
        remaining = remaining.substring(match.range.last + 1).trimStart()
    }

    return SlashArguments(
        named = named,
        unnamed = remaining.replacePipeMacro(pipe).trim(),
    )
}

private fun stripLeadingNamedArguments(args: String): String {
    var remaining = args.trimStart()

    while (true) {
        val match = leadingNamedArgumentRegex.find(remaining) ?: return remaining
        remaining = remaining.substring(match.range.last + 1).trimStart()
    }
}

private val leadingNamedArgumentRegex =
    Regex("""^([A-Za-z_][A-Za-z0-9_-]*)=(?:"((?:\\.|[^"])*)"|'((?:\\.|[^'])*)'|(\S+))\s*""")

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
