package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.extractContextForMatching
import me.rerere.rikkahub.data.model.isTriggered
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 提示词注入转换器
 *
 * 根据 Assistant 关联的 ModeInjection 和 Lorebook 进行提示词注入
 */
object PromptInjectionTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // 绑定了可解析的预设时，世界书由预设转换器处理，跳过避免重复注入
        if (ctx.assistant.promptPresetId != null ||
            hasResolvedTextCompletionPresetBinding(ctx.assistant, ctx.settings)
        ) return messages
        return transformMessages(
            messages = messages,
            assistant = ctx.assistant,
            modeInjections = ctx.settings.modeInjections,
            lorebooks = ctx.settings.lorebooks,
            conversationModeInjectionIds = ctx.conversationModeInjectionIds,
            conversationLorebookIds = ctx.conversationLorebookIds,
        )
    }
}

/**
 * 核心注入逻辑（可测试的纯函数）
 */
internal fun transformMessages(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
): List<UIMessage> {
    // 收集所有需要注入的内容
    val injections = collectInjections(
        messages = messages,
        assistant = assistant,
        modeInjections = modeInjections,
        lorebooks = lorebooks,
        conversationModeInjectionIds = conversationModeInjectionIds,
        conversationLorebookIds = conversationLorebookIds,
    )

    if (injections.isEmpty()) {
        return messages
    }

    // 按位置和优先级分组
    val byPosition = injections
        .sortedByDescending { it.priority }
        .groupBy { it.position }

    // 应用注入
    return applyInjections(messages, byPosition)
}

/**
 * 收集需要注入的内容
 *
 * @param roll 概率判定函数(传入 0~100 的概率, 返回是否命中)，默认使用真实随机数，
 *             测试时可注入确定性实现。
 */
internal fun collectInjections(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>,
    conversationModeInjectionIds: Set<Uuid> = emptySet(),
    conversationLorebookIds: Set<Uuid> = emptySet(),
    roll: (Int) -> Boolean = ::defaultRoll,
): List<PromptInjection> {
    val injections = mutableListOf<PromptInjection>()
    val effectiveModeInjectionIds = if (assistant.allowConversationPromptInjection) {
        conversationModeInjectionIds
    } else {
        assistant.modeInjectionIds
    }
    val effectiveLorebookIds = if (assistant.allowConversationPromptInjection) {
        conversationLorebookIds
    } else {
        assistant.lorebookIds
    }

    // 1. 获取关联的 ModeInjection
    modeInjections
        .filter { it.enabled && effectiveModeInjectionIds.contains(it.id) }
        .forEach { injections.add(it) }

    // 2. 获取关联的 Lorebook 中被触发的 RegexInjection
    val enabledLorebooks = lorebooks.filter {
        it.enabled && effectiveLorebookIds.contains(it.id)
    }
    if (enabledLorebooks.isNotEmpty()) {
        // 提取上下文用于匹配（只取非 SYSTEM 消息）
        val nonSystemMessages = messages.filter { it.role != MessageRole.SYSTEM }
        val allEntries = enabledLorebooks.flatMap { it.entries }.filter { it.enabled }

        // 初始激活：按各条目 scanDepth 扫描对话；delayUntilRecursion 的条目跳过
        val initiallyActivated = allEntries.filter { entry ->
            if (entry.delayUntilRecursion) return@filter false
            val context = extractContextForMatching(nonSystemMessages, entry.scanDepth)
            entry.isTriggered(context)
        }

        // 递归激活：用已激活条目的内容继续触发其他条目
        val activated = expandRecursive(allEntries, initiallyActivated)

        // 应用概率判定与包含组筛选
        val selected = selectTriggeredEntries(activated, roll)

        // 应用字符预算（取启用世界书中最大的非零预算）
        val budget = enabledLorebooks.mapNotNull { it.tokenBudget.takeIf { b -> b > 0 } }.maxOrNull() ?: 0
        injections.addAll(applyTokenBudget(selected, budget))
    }

    return injections
}

/**
 * 字符预算筛选（纯函数，便于测试）。
 *
 * budget <= 0 表示不限制。否则按优先级从高到低累计内容长度，
 * 常驻(constantActive)条目始终保留，超出预算的非常驻条目被丢弃。
 */
internal fun applyTokenBudget(
    entries: List<PromptInjection.RegexInjection>,
    budget: Int,
): List<PromptInjection.RegexInjection> {
    if (budget <= 0) return entries
    // 常驻条目优先保留，其余按优先级降序竞争预算
    val (constant, optional) = entries.partition { it.constantActive }
    var used = constant.sumOf { it.content.length }
    val kept = constant.toMutableList()
    optional.sortedByDescending { it.priority }.forEach { entry ->
        val cost = entry.content.length
        if (used + cost <= budget) {
            kept.add(entry)
            used += cost
        }
    }
    // 保持原有顺序
    return entries.filter { it in kept }
}

/** 默认最大递归步数 */
internal const val DEFAULT_MAX_RECURSION_STEPS = 3

/**
 * 世界书递归激活（纯函数，便于测试）。
 *
 * 用已激活条目的内容作为扫描缓冲，继续触发其他条目，直到无新增或达到步数上限：
 * - preventRecursion 的条目内容不进入扫描缓冲（不触发其他条目）
 * - excludeRecursion 的条目不会被递归激活（只能由对话激活）
 * - delayUntilRecursion 的条目只能在递归阶段被激活
 */
internal fun expandRecursive(
    allEntries: List<PromptInjection.RegexInjection>,
    initiallyActivated: List<PromptInjection.RegexInjection>,
    maxSteps: Int = DEFAULT_MAX_RECURSION_STEPS,
): List<PromptInjection.RegexInjection> {
    val activated = LinkedHashSet(initiallyActivated)
    var frontier = initiallyActivated
    var step = 0
    while (step < maxSteps && frontier.isNotEmpty()) {
        val buffer = frontier
            .filterNot { it.preventRecursion }
            .joinToString("\n") { it.content }
        if (buffer.isBlank()) break
        val newly = allEntries.filter { entry ->
            entry !in activated && !entry.excludeRecursion && entry.isTriggered(buffer)
        }
        if (newly.isEmpty()) break
        activated.addAll(newly)
        frontier = newly
        step++
    }
    return activated.toList()
}

/**
 * 默认概率判定：概率 >=100 必中，<=0 必不中，否则按随机数判定。
 */
internal fun defaultRoll(probability: Int): Boolean = when {
    probability >= 100 -> true
    probability <= 0 -> false
    else -> Random.nextInt(100) < probability
}

/**
 * 对被触发的世界书条目应用概率判定与包含组筛选（纯函数，便于测试）。
 *
 * - useProbability 为 true 时按 probability 判定是否保留
 * - 同一非空 inclusionGroup 中仅保留优先级最高的一条
 */
internal fun selectTriggeredEntries(
    entries: List<PromptInjection.RegexInjection>,
    roll: (Int) -> Boolean = ::defaultRoll,
): List<PromptInjection.RegexInjection> {
    val afterProbability = entries.filter { entry ->
        if (entry.useProbability) roll(entry.probability) else true
    }
    val (grouped, ungrouped) = afterProbability.partition { it.inclusionGroup.isNotBlank() }
    val groupWinners = grouped
        .groupBy { it.inclusionGroup }
        .values
        .mapNotNull { group -> group.maxByOrNull { it.priority } }
    return ungrouped + groupWinners
}

/**
 * 应用注入到消息列表
 */
internal fun applyInjections(
    messages: List<UIMessage>,
    byPosition: Map<InjectionPosition, List<PromptInjection>>
): List<UIMessage> {
    val result = messages.toMutableList()

    // 找到系统消息的索引（通常是第一条）
    val systemIndex = result.indexOfFirst { it.role == MessageRole.SYSTEM }

    // 处理 BEFORE_SYSTEM_PROMPT 和 AFTER_SYSTEM_PROMPT
    if (systemIndex >= 0) {
        val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""
        val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""

        if (beforeContent.isNotEmpty() || afterContent.isNotEmpty()) {
            val systemMessage = result[systemIndex]
            val originalText = systemMessage.parts
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("") { it.text }

            val newText = buildString {
                if (beforeContent.isNotEmpty()) {
                    append(beforeContent)
                    appendLine()
                }
                append(originalText)
                if (afterContent.isNotEmpty()) {
                    appendLine()
                    append(afterContent)
                }
            }

            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText))
            )
        }
    } else {
        // 没有系统消息时，创建一个新的系统消息
        val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""
        val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
            ?.joinToString("\n") { it.content } ?: ""

        val combinedContent = buildString {
            if (beforeContent.isNotEmpty()) {
                append(beforeContent)
            }
            if (afterContent.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                append(afterContent)
            }
        }

        if (combinedContent.isNotEmpty()) {
            result.add(0, UIMessage.system(combinedContent))
        }
    }

    // 处理 TOP_OF_CHAT：在第一条用户消息之前插入
    val topInjections = byPosition[InjectionPosition.TOP_OF_CHAT]
    if (!topInjections.isNullOrEmpty()) {
        // 重新计算索引（因为可能插入了系统消息）
        var insertIndex = result.indexOfFirst { it.role == MessageRole.USER }
            .takeIf { it >= 0 } ?: result.size
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(topInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 BOTTOM_OF_CHAT：在最后一条消息之前插入
    val bottomInjections = byPosition[InjectionPosition.BOTTOM_OF_CHAT]
    if (!bottomInjections.isNullOrEmpty()) {
        var insertIndex = (result.size - 1).coerceAtLeast(0)
        insertIndex = findSafeInsertIndex(result, insertIndex)
        createMergedInjectionMessages(bottomInjections).forEach { message ->
            result.add(insertIndex, message)
            insertIndex++
        }
    }

    // 处理 AT_DEPTH：在指定深度位置插入（从最新消息往前数）
    // 按 injectDepth 分组，相同深度的合并，按深度从大到小处理（避免索引变化问题）
    val atDepthInjections = byPosition[InjectionPosition.AT_DEPTH]
    if (!atDepthInjections.isNullOrEmpty()) {
        val byDepth = atDepthInjections.groupBy { it.injectDepth }
        byDepth.keys.sortedDescending().forEach { depth ->
            val injections = byDepth[depth] ?: return@forEach
            // 计算插入位置：result.size - depth，但要确保在有效范围内
            // depth=1 表示在最后一条消息之前，depth=2 表示在倒数第二条之前...
            var insertIndex = (result.size - depth.coerceAtLeast(1)).coerceIn(0, result.size)
            insertIndex = findSafeInsertIndex(result, insertIndex)
            createMergedInjectionMessages(injections).forEach { message ->
                result.add(insertIndex, message)
                insertIndex++
            }
        }
    }

    return result
}

/**
 * 将同一 role 的注入合并成消息列表
 * 按 role 分组后合并内容，返回合并后的消息列表
 */
private fun createMergedInjectionMessages(injections: List<PromptInjection>): List<UIMessage> {
    return injections
        .groupBy { it.role }
        .map { (role, grouped) ->
            val mergedContent = grouped.joinToString("\n") { it.content }
            when (role) {
                MessageRole.ASSISTANT -> UIMessage.assistant(mergedContent)
                else -> UIMessage.user(mergedContent)
            }
        }
}

/**
 * 查找安全的插入位置，避免注入到 USER → ASSISTANT(含Tool) 之间
 *
 * 某些提供商（如 deepseek）要求 USER 之后紧跟带工具的 ASSISTANT，
 * 在两者之间插入消息会导致报错或破坏推理连续性。
 */
internal fun findSafeInsertIndex(messages: List<UIMessage>, targetIndex: Int): Int {
    var index = targetIndex.coerceIn(0, messages.size)

    // 向前查找，直到找到一个安全的位置
    while (index > 0) {
        val prevMessage = messages.getOrNull(index - 1)
        val currentMessage = messages.getOrNull(index)

        // 不能插入到 USER → ASSISTANT(含Tool) 之间
        val isPrevUser = prevMessage?.role == MessageRole.USER
        val isCurrentAssistantWithTools = currentMessage?.role == MessageRole.ASSISTANT
            && currentMessage.getTools().isNotEmpty()

        if (isPrevUser && isCurrentAssistantWithTools) {
            index--
        } else {
            break
        }
    }

    return index
}
