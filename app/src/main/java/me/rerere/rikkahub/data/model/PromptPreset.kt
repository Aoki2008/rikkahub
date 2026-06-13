package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * SillyTavern Chat Completion 预设（"酒馆预设"）。
 *
 * 核心是一组可排序、可开关的 prompt 块（[prompts] + [promptOrder]）：
 * - marker 块（charDescription/scenario/worldInfoBefore/dialogueExamples/chatHistory…）
 *   由组装引擎用角色卡/世界书/聊天记录/人设填充；
 * - 普通文本块（main/nsfw/jailbreak/自定义）是预设里写死的内容（越狱、风格指令等）。
 *
 * 由 [me.rerere.rikkahub.data.ai.PromptManagerAssembler] 按 promptOrder 组装成
 * 真正发送给 API 的消息列表。
 */
@Serializable
data class PromptPreset(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val prompts: List<PresetPrompt> = emptyList(),
    val promptOrder: List<PromptOrderEntry> = emptyList(),
    // 采样参数（null = 不覆盖助手/全局设置）
    val temperature: Float? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val maxContext: Int? = null,
    val maxTokens: Int? = null,
    // 工具/格式提示词
    val scenarioFormat: String = "{{scenario}}",
    val personalityFormat: String = "{{personality}}",
    val wiFormat: String = "{0}",
    val newChatPrompt: String = "",
    val newExampleChatPrompt: String = "",
    val squashSystemMessages: Boolean = false,
    // names_behavior: 0=default(不加名), 1=完成时加, 2=总是加名前缀
    val namesBehavior: Int = 0,
) {
    /** 按 promptOrder 顺序返回启用的块（identifier 在 prompts 中存在的） */
    fun orderedEnabledPrompts(): List<PresetPrompt> {
        val byId = prompts.associateBy { it.identifier }
        return promptOrder
            .filter { it.enabled }
            .mapNotNull { byId[it.identifier] }
    }
}

/**
 * 单个 prompt 块。
 *
 * marker=true 时 [content] 通常为空，由引擎按 [identifier] 填充动态内容。
 */
@Serializable
data class PresetPrompt(
    val identifier: String = "",
    val name: String = "",
    val role: String = "system",      // system / user / assistant
    val content: String = "",
    @SerialName("system_prompt")
    val systemPrompt: Boolean = false,
    val marker: Boolean = false,
    // 绝对位置注入（部分块支持）：0=相对(默认), 1=按深度
    @SerialName("injection_position")
    val injectionPosition: Int? = null,
    @SerialName("injection_depth")
    val injectionDepth: Int? = null,
)

@Serializable
data class PromptOrderEntry(
    val identifier: String = "",
    val enabled: Boolean = true,
)

/**
 * SillyTavern 内置 marker 标识符。
 */
object PromptMarkers {
    const val MAIN = "main"
    const val NSFW = "nsfw"
    const val JAILBREAK = "jailbreak"
    const val ENHANCE_DEFINITIONS = "enhanceDefinitions"
    const val CHAR_DESCRIPTION = "charDescription"
    const val CHAR_PERSONALITY = "charPersonality"
    const val SCENARIO = "scenario"
    const val PERSONA_DESCRIPTION = "personaDescription"
    const val WORLD_INFO_BEFORE = "worldInfoBefore"
    const val WORLD_INFO_AFTER = "worldInfoAfter"
    const val DIALOGUE_EXAMPLES = "dialogueExamples"
    const val CHAT_HISTORY = "chatHistory"
}
