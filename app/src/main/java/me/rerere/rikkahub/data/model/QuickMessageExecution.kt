package me.rerere.rikkahub.data.model

data class QuickMessageExecutionPlan(
    val inputText: String,
    val sendMode: QuickMessageSendMode,
    val unsupportedCommands: List<String> = emptyList(),
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
        executeQuickMessageSlashCommands(input)?.let { return it }
        return QuickMessageExecutionPlan(
            inputText = input,
            sendMode = QuickMessageSendMode.NONE,
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

private fun executeQuickMessageSlashCommands(input: String): QuickMessageExecutionPlan? {
    var inputText = ""
    var pipe = ""
    var sendMode = QuickMessageSendMode.NONE
    val unsupported = mutableListOf<String>()

    for (commandText in splitSlashPipeline(input)) {
        val command = parseSlashCommand(commandText)
        if (command == null) {
            if (commandText.isNotBlank()) unsupported += commandText.trim()
            continue
        }

        val args = stripLeadingNamedArguments(command.args).trim()
        when (command.name.lowercase()) {
            "setinput", "input" -> {
                inputText = resolveSlashArgument(args, pipe)
                pipe = inputText
                sendMode = QuickMessageSendMode.NONE
            }

            "send" -> {
                inputText = resolveSlashArgument(args, pipe)
                pipe = inputText
                sendMode = QuickMessageSendMode.WITHOUT_RESPONSE
            }

            "trigger", "gen", "generate" -> {
                if (inputText.isBlank() && pipe.isNotBlank()) {
                    inputText = pipe
                }
                sendMode = QuickMessageSendMode.NORMAL
            }

            "echo", "return" -> {
                pipe = unquoteSlashArgument(args)
            }

            "/", "#", "breakpoint", "parser-flag" -> Unit

            else -> unsupported += "/${command.name}"
        }
    }

    if (inputText.isBlank() && unsupported.isNotEmpty()) {
        return null
    }

    return QuickMessageExecutionPlan(
        inputText = inputText,
        sendMode = sendMode,
        unsupportedCommands = unsupported,
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

private fun stripLeadingNamedArguments(args: String): String {
    var remaining = args.trimStart()
    val namedArg = Regex("""^[A-Za-z_][A-Za-z0-9_-]*=(?:"(?:\\.|[^"])*"|'(?:\\.|[^'])*'|\S+)\s*""")

    while (true) {
        val match = namedArg.find(remaining) ?: return remaining
        remaining = remaining.substring(match.range.last + 1).trimStart()
    }
}

private fun unquoteSlashArgument(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length < 2) return trimmed
    val first = trimmed.first()
    val last = trimmed.last()
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return trimmed
            .substring(1, trimmed.lastIndex)
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
    }
    return trimmed
}

private fun resolveSlashArgument(args: String, pipe: String): String =
    if (args.isBlank()) pipe else unquoteSlashArgument(args)
