package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.PresetPrompt
import me.rerere.rikkahub.data.model.PromptMarkers
import me.rerere.rikkahub.data.model.PromptPreset

/**
 * Prompt Manager 组装引擎（纯函数，便于测试）。
 *
 * 按 SillyTavern Chat Completion 预设的 promptOrder，把各 prompt 块组装成发送给
 * API 的消息列表。marker 块用角色卡/世界书/人设/聊天记录填充；文本块按其 role 原样
 * 输出（其中的 {{char}}/{{user}} 等宏由后续 PlaceholderTransformer 解析）。
 */
data class PromptAssemblyInput(
    val preset: PromptPreset,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val exampleMessages: String = "",
    val personaDescription: String = "",
    val worldInfoBefore: List<String> = emptyList(),
    val worldInfoAfter: List<String> = emptyList(),
    val chatHistory: List<UIMessage> = emptyList(),
)

object PromptManagerAssembler {

    fun assemble(input: PromptAssemblyInput): List<UIMessage> {
        val result = mutableListOf<UIMessage>()
        val preset = input.preset

        for (block in preset.orderedEnabledPrompts()) {
            when {
                block.marker -> appendMarker(result, block, input, preset)
                else -> {
                    val content = block.content
                    if (content.isNotBlank()) {
                        result.add(message(block.role, content))
                    }
                }
            }
        }

        return if (preset.squashSystemMessages) squashSystem(result) else result
    }

    private fun appendMarker(
        out: MutableList<UIMessage>,
        block: PresetPrompt,
        input: PromptAssemblyInput,
        preset: PromptPreset,
    ) {
        when (block.identifier) {
            PromptMarkers.CHAR_DESCRIPTION ->
                input.description.ifBlank { null }?.let { out.add(UIMessage.system(it)) }

            PromptMarkers.CHAR_PERSONALITY ->
                input.personality.ifBlank { null }?.let {
                    out.add(UIMessage.system(preset.personalityFormat.replace("{{personality}}", it)))
                }

            PromptMarkers.SCENARIO ->
                input.scenario.ifBlank { null }?.let {
                    out.add(UIMessage.system(preset.scenarioFormat.replace("{{scenario}}", it)))
                }

            PromptMarkers.PERSONA_DESCRIPTION ->
                input.personaDescription.ifBlank { null }?.let { out.add(UIMessage.system(it)) }

            PromptMarkers.WORLD_INFO_BEFORE ->
                joinWorldInfo(input.worldInfoBefore, preset)?.let { out.add(UIMessage.system(it)) }

            PromptMarkers.WORLD_INFO_AFTER ->
                joinWorldInfo(input.worldInfoAfter, preset)?.let { out.add(UIMessage.system(it)) }

            PromptMarkers.DIALOGUE_EXAMPLES ->
                input.exampleMessages.ifBlank { null }?.let {
                    val prefix = preset.newExampleChatPrompt.ifBlank { "" }
                    val text = if (prefix.isNotBlank()) "$prefix\n$it" else it
                    out.add(UIMessage.system(text))
                }

            PromptMarkers.CHAT_HISTORY -> {
                if (preset.newChatPrompt.isNotBlank()) {
                    out.add(UIMessage.system(preset.newChatPrompt))
                }
                out.addAll(input.chatHistory)
            }

            else -> {
                // 未知 marker：若有写死内容则按 role 输出，否则忽略
                if (block.content.isNotBlank()) out.add(message(block.role, block.content))
            }
        }
    }

    private fun joinWorldInfo(entries: List<String>, preset: PromptPreset): String? {
        val nonEmpty = entries.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) return null
        val joined = nonEmpty.joinToString("\n")
        return if (preset.wiFormat.contains("{0}")) {
            preset.wiFormat.replace("{0}", joined)
        } else {
            joined
        }
    }

    private fun message(role: String, content: String): UIMessage = when (role.lowercase()) {
        "user" -> UIMessage.user(content)
        "assistant" -> UIMessage.assistant(content)
        else -> UIMessage.system(content)
    }

    /** 合并相邻的 system 消息（squash_system_messages） */
    private fun squashSystem(messages: List<UIMessage>): List<UIMessage> {
        val result = mutableListOf<UIMessage>()
        for (msg in messages) {
            val last = result.lastOrNull()
            if (msg.role == MessageRole.SYSTEM && last?.role == MessageRole.SYSTEM) {
                val mergedText = textOf(last) + "\n\n" + textOf(msg)
                result[result.lastIndex] = UIMessage.system(mergedText)
            } else {
                result.add(msg)
            }
        }
        return result
    }

    private fun textOf(message: UIMessage): String =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
}
