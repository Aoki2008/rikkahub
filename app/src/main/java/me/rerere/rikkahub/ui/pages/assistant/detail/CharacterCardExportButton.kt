package me.rerere.rikkahub.ui.pages.assistant.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.export.CharacterCardExporter
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.PngTextChunk
import java.io.ByteArrayOutputStream

/**
 * 将助手导出为 SillyTavern 角色卡：
 * - JSON：通过文件选择器保存 `chara_card_v3` JSON（始终可用）。
 * - PNG：当助手头像为图片时，把卡片 JSON 以 base64 写入头像 PNG 的 tEXt 块
 *   (关键字 `chara`(V2 兼容) 与 `ccv3`(V3))，生成可被 SillyTavern 导入的 PNG 角色卡。
 *
 * 复用 [CharacterCardExporter] 构建与导入对称的卡片 JSON，自动附带助手绑定的世界书；
 * PNG 嵌入复用 [PngTextChunk]。
 */
@Composable
fun CharacterCardExportButton(
    assistant: Assistant,
    lorebooks: List<Lorebook>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    fun cardJson(): String {
        val bound = lorebooks.filter { it.id in assistant.lorebookIds }
        return CharacterCardExporter.buildV3Card(assistant, bound).toString()
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = cardJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                toaster.show("Character card exported (JSON)", type = ToastType.Success)
            }.onFailure { e ->
                e.printStackTrace()
                toaster.show(e.message ?: "Export failed", type = ToastType.Error)
            }
        }
    }

    val avatarUrl = (assistant.avatar as? Avatar.Image)?.url

    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = cardJson()
                val source = avatarUrl ?: error("No avatar image to embed into")
                withContext(Dispatchers.IO) {
                    // 读取头像并统一重编码为 PNG，确保是合法 PNG
                    val pngBytes = context.contentResolver.openInputStream(source.toUri())?.use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                            ?: error("Cannot decode avatar image")
                        ByteArrayOutputStream().use { bos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                            bos.toByteArray()
                        }
                    } ?: error("Cannot read avatar image")

                    val base64 = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    // 同时写入 chara(V2) 与 ccv3(V3) 关键字以最大化兼容性
                    val withV2 = PngTextChunk.addTextChunk(pngBytes, "chara", base64)
                    val card = PngTextChunk.addTextChunk(withV2, "ccv3", base64)

                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(card)
                    } ?: error("Cannot open output stream")
                }
            }.onSuccess {
                toaster.show("Character card exported (PNG)", type = ToastType.Success)
            }.onFailure { e ->
                e.printStackTrace()
                toaster.show(e.message ?: "Export failed", type = ToastType.Error)
            }
        }
    }

    val safeName = assistant.name.ifBlank { "character" }.replace(Regex("[^A-Za-z0-9_-]"), "_")

    Column(
        modifier = modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { jsonLauncher.launch("$safeName.json") }) {
            Text("Export character card (JSON)")
        }
        if (avatarUrl != null) {
            OutlinedButton(onClick = { pngLauncher.launch("$safeName.png") }) {
                Text("Export character card (PNG)")
            }
        }
    }
}
