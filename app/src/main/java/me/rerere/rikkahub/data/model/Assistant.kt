package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageSize: Int = 0,
    val streamOutput: Boolean = true,
    val enableMemory: Boolean = false,
    val useGlobalMemory: Boolean = false, // 使用全局共享记忆而非助手隔离记忆
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val background: String? = null, // 聊天页背景图地址(本地文件 URI 或网络 URL), 为 null 时无背景
    val backgroundOpacity: Float = 1.0f, // 背景图不透明度(0~1)
    val useGradientBackground: Boolean = false, // 开启后聊天页使用动态渐变背景
    val modeInjectionIds: Set<Uuid> = emptySet(),      // 关联的模式注入 ID
    val lorebookIds: Set<Uuid> = emptySet(),            // 关联的 Lorebook ID
    val enabledSkills: Set<String> = emptySet(),        // 启用的 skill 名称列表
    val enableTimeReminder: Boolean = false,            // 时间间隔提醒注入
    val allowConversationSystemPrompt: Boolean = false, // 允许对话单独重写 system prompt
    val allowConversationPromptInjection: Boolean = false, // 允许对话单独绑定提示词注入
    val characterCard: CharacterCard? = null, // 导入的 SillyTavern 角色卡原始字段(用于宏与重建提示词)
    val authorsNote: AuthorsNote = AuthorsNote(), // 作者注释(深度注入)
    val promptPresetId: Uuid? = null, // 绑定的 Chat Completion 预设(酒馆预设); 非空时用 Prompt Manager 组装
    val contextPresetId: Uuid? = null, // 绑定的 Text Completion context 预设
    val instructPresetId: Uuid? = null, // 绑定的 Text Completion instruct 预设
    val systemPromptPresetId: Uuid? = null, // 绑定的 Text Completion system prompt 预设(可选)
)

/**
 * 作者注释 (Author's Note)
 *
 * 对应 SillyTavern 的 Author's Note：一段按配置深度注入到对话历史中的文本，
 * 通常用于稳定地引导风格/剧情走向。支持插入频率(interval)。
 */
@Serializable
data class AuthorsNote(
    val enabled: Boolean = false,
    val content: String = "",
    val position: InjectionPosition = InjectionPosition.AT_DEPTH,
    val injectDepth: Int = 4,
    val role: MessageRole = MessageRole.USER,
    val interval: Int = 1, // 每隔 N 条消息插入一次(1 = 每次都插入)
)

/**
 * SillyTavern 角色卡原始字段 (V2/V3 spec)
 *
 * 保留导入时的原始字段，使 {{description}}/{{personality}}/{{scenario}} 等宏可用，
 * 并支持将来基于上下文模板重建提示词、备选问候语切换等功能。
 */
@Serializable
data class CharacterCard(
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val mesExample: String = "",
    val creatorNotes: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val tags: List<String> = emptyList(),
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val postHistoryInstructions: String = "",
)

@Serializable
data class QuickMessage(
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val content: String = "",
)

@Serializable
data class AssistantMemory(
    val id: Int,
    val content: String = "",
)

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}

@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // 正则表达式
    val replaceString: String = "", // 替换字符串
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // 是否仅在视觉上影响
)

fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            try {
                val result = acc.replace(
                    regex = Regex(regex.findRegex),
                    replacement = regex.replaceString,
                )
                // println("Regex: ${regex.findRegex} -> ${result}")
                result
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果正则表达式格式错误，返回原字符串
                acc
            }
        } else {
            acc
        }
    }
}

/**
 * 注入位置
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system_prompt")
    BEFORE_SYSTEM_PROMPT,   // 系统提示词之前

    @SerialName("after_system_prompt")
    AFTER_SYSTEM_PROMPT,    // 系统提示词之后（最常用）

    @SerialName("top_of_chat")
    TOP_OF_CHAT,            // 对话最开头（第一条用户消息之前）

    @SerialName("bottom_of_chat")
    BOTTOM_OF_CHAT,         // 最新消息之前（当前用户输入之前）

    @SerialName("at_depth")
    AT_DEPTH,               // 在指定深度位置插入（从最新消息往前数）
}

/**
 * 世界书条目的次要关键词逻辑 (对应 SillyTavern world_info_logic)
 *
 * 仅当 secondaryKeys 非空时生效；否则只看主关键词。
 */
@Serializable
enum class SelectiveLogic {
    @SerialName("and_any")
    AND_ANY,   // 主关键词命中 且 任一次要关键词命中

    @SerialName("not_all")
    NOT_ALL,   // 主关键词命中 且 并非全部次要关键词命中

    @SerialName("not_any")
    NOT_ANY,   // 主关键词命中 且 无任何次要关键词命中

    @SerialName("and_all")
    AND_ALL,   // 主关键词命中 且 全部次要关键词命中
}

/**
 * 提示词注入
 *
 * - ModeInjection: 基于模式开关的注入（如学习模式）
 * - RegexInjection: 基于正则匹配的注入（Lorebook）
 */
@Serializable
sealed class PromptInjection {
    abstract val id: Uuid
    abstract val name: String
    abstract val enabled: Boolean
    abstract val priority: Int
    abstract val position: InjectionPosition
    abstract val content: String
    abstract val injectDepth: Int  // 当 position 为 AT_DEPTH 时使用，表示从最新消息往前数的位置
    abstract val role: MessageRole  // 注入角色：USER 或 ASSISTANT

    /**
     * 模式注入 - 基于开关状态触发
     */
    @Serializable
    @SerialName("mode")
    data class ModeInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
    ) : PromptInjection()

    /**
     * 正则注入 - 基于内容匹配触发（世界书）
     */
    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val keywords: List<String> = emptyList(),  // 触发关键词
        val useRegex: Boolean = false,             // 是否使用正则匹配
        val caseSensitive: Boolean = false,        // 大小写敏感
        val scanDepth: Int = 4,                    // 扫描最近N条消息
        val constantActive: Boolean = false,       // 常驻激活（无需匹配）
        val secondaryKeys: List<String> = emptyList(), // 次要关键词
        val selectiveLogic: SelectiveLogic = SelectiveLogic.AND_ANY, // 次要关键词逻辑
        val matchWholeWords: Boolean = false,      // 整词匹配
        val probability: Int = 100,                // 触发概率(0~100)
        val useProbability: Boolean = false,       // 是否启用概率
        val inclusionGroup: String = "",           // 包含组：同组内仅激活一个(优先级最高者)
        val excludeRecursion: Boolean = false,     // 不被其他条目的递归激活(仅匹配对话)
        val preventRecursion: Boolean = false,     // 本条目内容不参与递归扫描(不触发其他条目)
        val delayUntilRecursion: Boolean = false,  // 仅在递归阶段可被激活(初始扫描不激活)
    ) : PromptInjection()
}

/**
 * Lorebook - 组织管理多个 RegexInjection
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
    val tokenBudget: Int = 0, // 激活条目内容的字符预算上限(0 = 不限制)
)

/**
 * 检查单个关键词是否在上下文中出现。
 */
private fun keywordMatches(
    context: String,
    keyword: String,
    useRegex: Boolean,
    caseSensitive: Boolean,
    matchWholeWords: Boolean,
): Boolean {
    if (keyword.isBlank()) return false
    if (useRegex) {
        return try {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(keyword, options).containsMatchIn(context)
        } catch (e: Exception) {
            false
        }
    }
    if (matchWholeWords) {
        return try {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex("\\b${Regex.escape(keyword)}\\b", options).containsMatchIn(context)
        } catch (e: Exception) {
            context.contains(keyword, ignoreCase = !caseSensitive)
        }
    }
    return context.contains(keyword, ignoreCase = !caseSensitive)
}

/**
 * 检查 RegexInjection 是否被触发
 *
 * 逻辑（对齐 SillyTavern）：
 * 1. constantActive → 永远触发
 * 2. 主关键词需至少命中其一
 * 3. 若有次要关键词，按 selectiveLogic 进一步判定
 *
 * @param context 要扫描的上下文文本
 * @return 是否触发
 */
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false

    val primaryMatched = keywords.any {
        keywordMatches(context, it, useRegex, caseSensitive, matchWholeWords)
    }
    if (!primaryMatched) return false

    if (secondaryKeys.isEmpty()) return true

    val matchedSecondary = secondaryKeys.filter {
        keywordMatches(context, it, useRegex, caseSensitive, matchWholeWords)
    }
    return when (selectiveLogic) {
        SelectiveLogic.AND_ANY -> matchedSecondary.isNotEmpty()
        SelectiveLogic.AND_ALL -> matchedSecondary.size == secondaryKeys.size
        SelectiveLogic.NOT_ANY -> matchedSecondary.isEmpty()
        SelectiveLogic.NOT_ALL -> matchedSecondary.size < secondaryKeys.size
    }
}

/**
 * 从消息列表中提取用于匹配的上下文文本
 *
 * @param messages 消息列表
 * @param scanDepth 扫描深度（最近N条消息）
 * @return 拼接的文本内容
 */
fun extractContextForMatching(
    messages: List<UIMessage>,
    scanDepth: Int
): String {
    return messages
        .takeLast(scanDepth)
        .joinToString("\n") { it.toText() }
}

/**
 * 获取所有被触发的注入，按优先级排序
 *
 * @param injections 所有注入规则
 * @param context 上下文文本
 * @return 被触发的注入列表，按优先级降序排列
 */
fun getTriggeredInjections(
    injections: List<PromptInjection.RegexInjection>,
    context: String
): List<PromptInjection.RegexInjection> {
    return injections
        .filter { it.isTriggered(context) }
        .sortedByDescending { it.priority }
}
