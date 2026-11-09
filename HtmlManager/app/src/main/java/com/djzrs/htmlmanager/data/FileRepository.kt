package com.djzrs.htmlmanager.data

import android.content.Context
import java.io.File

/**
 * htmls/ 私有目录的唯一出入口。
 * 命名规则集中在 [normalizeName] / [isNameTaken]，页面只管展示结果。
 */
class FileRepository(context: Context) {

    /** htmls/ 目录本身，服务器也要用它。 */
    val directory: File = File(context.filesDir, HTML_DIR).apply { if (!exists()) mkdirs() }

    fun list(): List<HtmlFile> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { HtmlFile(it.name, it.lastModified()) }
            ?: emptyList()

    fun fileOf(name: String): File = File(directory, name)

    fun exists(name: String): Boolean = fileOf(name).exists()

    fun read(name: String): String = runCatching { fileOf(name).readText() }.getOrDefault("")

    fun write(name: String, content: String): Boolean =
        runCatching { fileOf(name).writeText(content) }.isSuccess

    fun delete(name: String): Boolean =
        runCatching { fileOf(name).delete() }.getOrDefault(false)

    /**
     * 规范化用户输入的文件名：trim → 空则返回 null → 非法字符替换为 `_` → 补 `.html` 后缀。
     */
    fun normalizeName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val sanitized = buildString {
            trimmed.forEach { char -> append(if (char in ILLEGAL_CHARS) '_' else char) }
        }
        return if (sanitized.endsWith(SUFFIX, ignoreCase = true)) sanitized else sanitized + SUFFIX
    }

    /**
     * 重名检查。编辑模式下文件名未变时视为覆盖自身，直接放行。
     */
    fun isNameTaken(name: String, originalName: String?): Boolean =
        name != originalName && exists(name)

    companion object {
        const val HTML_DIR = "htmls"
        const val SUFFIX = ".html"
        private val ILLEGAL_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    }
}
