package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.Locale
import java.util.TimeZone

data class PlaceholderCtx(
    val context: Context,
    val settingsStore: SettingsStore,
    val model: Model,
    val assistant: Assistant,
)

/** 当前选中的用户角色 (Persona)，未选中时为 null。 */
private fun PlaceholderCtx.activePersona(): me.rerere.rikkahub.data.model.Persona? {
    val settings = settingsStore.settingsFlow.value
    val id = settings.selectedPersonaId ?: return null
    return settings.personas.firstOrNull { it.id == id }
}

/** {{user}}/{{nickname}} 解析：优先使用选中 Persona 名称，回退到全局昵称。 */
private fun PlaceholderCtx.activeUserName(): String {
    val personaName = activePersona()?.name?.ifBlank { null }
    if (personaName != null) return personaName
    return settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
}

interface PlaceholderProvider {
    val placeholders: Map<String, PlaceholderInfo>
}

data class PlaceholderInfo(
    val displayName: @Composable () -> Unit,
    val resolver: (PlaceholderCtx) -> String
)

class PlaceholderBuilder {
    private val placeholders = mutableMapOf<String, PlaceholderInfo>()

    fun placeholder(
        key: String,
        displayName: @Composable () -> Unit,
        resolver: (PlaceholderCtx) -> String
    ) {
        placeholders[key] = PlaceholderInfo(displayName, resolver)
    }

    fun build(): Map<String, PlaceholderInfo> = placeholders.toMap()
}

fun buildPlaceholders(block: PlaceholderBuilder.() -> Unit): Map<String, PlaceholderInfo> {
    return PlaceholderBuilder().apply(block).build()
}

object DefaultPlaceholderProvider : PlaceholderProvider {
    override val placeholders: Map<String, PlaceholderInfo> = buildPlaceholders {
        placeholder("cur_date", { Text(stringResource(R.string.placeholder_current_date)) }) {
            LocalDate.now().toDateString()
        }

        placeholder("cur_time", { Text(stringResource(R.string.placeholder_current_time)) }) {
            LocalTime.now().toTimeString()
        }

        placeholder("cur_datetime", { Text(stringResource(R.string.placeholder_current_datetime)) }) {
            LocalDateTime.now().toDateTimeString()
        }

        placeholder("model_id", { Text(stringResource(R.string.placeholder_model_id)) }) {
            it.model.modelId
        }

        placeholder("model_name", { Text(stringResource(R.string.placeholder_model_name)) }) {
            it.model.displayName
        }

        placeholder("locale", { Text(stringResource(R.string.placeholder_locale)) }) {
            Locale.getDefault().displayName
        }

        placeholder("timezone", { Text(stringResource(R.string.placeholder_timezone)) }) {
            TimeZone.getDefault().displayName
        }

        placeholder("system_version", { Text(stringResource(R.string.placeholder_system_version)) }) {
            "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
        }

        placeholder("device_info", { Text(stringResource(R.string.placeholder_device_info)) }) {
            "${Build.BRAND} ${Build.MODEL}"
        }

        placeholder("battery_level", { Text(stringResource(R.string.placeholder_battery_level)) }) {
            it.context.batteryLevel().toString()
        }

        placeholder("nickname", { Text(stringResource(R.string.placeholder_nickname)) }) {
            it.activeUserName()
        }

        placeholder("char", { Text(stringResource(R.string.placeholder_char)) }) {
            it.assistant.name.ifBlank { "assistant" }
        }

        placeholder("user", { Text(stringResource(R.string.placeholder_user)) }) {
            it.activeUserName()
        }

        // SillyTavern 用户角色描述宏
        placeholder("persona", { Text("Persona Description") }) {
            it.activePersona()?.description.orEmpty()
        }

        // SillyTavern 角色卡字段宏：来自导入的 characterCard
        placeholder("description", { Text("Character Description") }) {
            it.assistant.characterCard?.description.orEmpty()
        }

        placeholder("personality", { Text("Character Personality") }) {
            it.assistant.characterCard?.personality.orEmpty()
        }

        placeholder("scenario", { Text("Scenario") }) {
            it.assistant.characterCard?.scenario.orEmpty()
        }
    }

    private fun Temporal.toDateString() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toTimeString() = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toDateTimeString() = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Context.batteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

object PlaceholderTransformer : InputMessageTransformer, KoinComponent {
    private val defaultProvider = DefaultPlaceholderProvider

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val settingsStore = get<SettingsStore>()
        return messages.map {
            it.copy(
                parts = it.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        part.copy(
                            text = replacePlaceholders(text = part.text, ctx = ctx, settingsStore = settingsStore)
                        )
                    } else {
                        part
                    }
                }
            )
        }
    }

    private fun replacePlaceholders(
        text: String,
        ctx: TransformerContext,
        settingsStore: SettingsStore
    ): String {
        var result = text

        val ctx = PlaceholderCtx(
            context = ctx.context,
            settingsStore = settingsStore,
            model = ctx.model,
            assistant = ctx.assistant
        )
        defaultProvider.placeholders.forEach { (key, placeholderInfo) ->
            val value = placeholderInfo.resolver(ctx)
            result = result
                .replace(oldValue = "{{$key}}", newValue = value, ignoreCase = true)
                .replace(oldValue = "{$key}", newValue = value, ignoreCase = true)
        }

        // SillyTavern 参数化宏：{{random:..}} {{pick:..}} {{roll:..}} {{newline}} 以及注释
        result = expandParametricMacros(result)

        return result
    }
}

/**
 * 展开 SillyTavern 风格的参数化宏（纯函数，便于单元测试）。
 *
 * 支持：
 * - `{{newline}}` → 换行符
 * - `{{// 注释}}` / `{{comment ...}}` → 移除
 * - `{{random:a,b,c}}` 或 `{{random::a::b}}` → 随机选择其一
 * - `{{pick:a,b,c}}` → 随机选择其一（与 random 行为一致）
 * - `{{roll:NdM}}` / `{{roll:N}}` / `{{roll:dM}}` → 掷骰求和
 */
internal fun expandParametricMacros(input: String): String {
    var result = input

    // 注释：{{// ...}} 与 {{comment ...}}
    result = SILLY_TAVERN_SLASH_COMMENT_REGEX.replace(result, "")
    result = SILLY_TAVERN_COMMENT_REGEX.replace(result, "")

    // {{newline}}
    result = SILLY_TAVERN_NEWLINE_REGEX.replace(result, "\n")

    // {{random:...}} 与 {{pick:...}}
    result = SILLY_TAVERN_RANDOM_PICK_REGEX.replace(result) { match ->
        val raw = match.groupValues[2]
        val options = if (raw.contains("::")) {
            raw.split("::")
        } else {
            raw.split(",")
        }.map { it.trim() }.filter { it.isNotEmpty() }
        if (options.isEmpty()) "" else options.random()
    }

    // {{roll:NdM}} / {{roll:N}} / {{roll:dM}}
    result = SILLY_TAVERN_ROLL_REGEX.replace(result) { match ->
        rollDice(match.groupValues[1].trim()).toString()
    }

    return result
}

private val SILLY_TAVERN_SLASH_COMMENT_REGEX = Regex("""\{\{//[\s\S]*?\}\}""")
private val SILLY_TAVERN_COMMENT_REGEX = Regex("""\{\{comment[\s\S]*?\}\}""", RegexOption.IGNORE_CASE)
private val SILLY_TAVERN_NEWLINE_REGEX = Regex("""\{\{newline\}\}""", RegexOption.IGNORE_CASE)
private val SILLY_TAVERN_RANDOM_PICK_REGEX = Regex("""\{\{(random|pick):+(.*?)\}\}""", RegexOption.IGNORE_CASE)
private val SILLY_TAVERN_ROLL_REGEX = Regex("""\{\{roll:(.*?)\}\}""", RegexOption.IGNORE_CASE)

private fun rollDice(spec: String): Int {
    val cleaned = spec.removePrefix("d").let { if (spec.startsWith("d", ignoreCase = true)) "1d$it" else spec }
    val parts = cleaned.split(Regex("d", RegexOption.IGNORE_CASE))
    return when (parts.size) {
        1 -> parts[0].toIntOrNull()?.let { (1..it.coerceAtLeast(1)).random() } ?: 0
        2 -> {
            val count = parts[0].toIntOrNull()?.coerceIn(1, 100) ?: 1
            val sides = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 6
            (1..count).sumOf { (1..sides).random() }
        }

        else -> 0
    }
}
