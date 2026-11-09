package com.djzrs.htmlmanager.ui.edit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import com.djzrs.htmlmanager.data.FileRepository

sealed interface SaveResult {
    data class Success(val name: String) : SaveResult
    data class Failure(val message: String) : SaveResult
}

class EditViewModel(
    application: Application,
    private val originalName: String?
) : AndroidViewModel(application) {

    private val repository = FileRepository(application)

    /** 编辑模式下载入的原始内容；新建模式为空。 */
    private val initialCode: String = originalName?.let { repository.read(it) } ?: ""

    /**
     * 「已落盘」的基准：内容与文件名。保存成功后一起往前挪，
     * 这样再按返回不会再问「要不要保存」，重复保存也不会把自己当成重名。
     */
    private var savedCode: String = initialCode
    private var savedName: String? = originalName

    var fileName by mutableStateOf(originalName ?: "")
        private set

    var code by mutableStateOf(TextFieldValue(initialCode))
        private set

    var exitDialogVisible by mutableStateOf(false)
        private set

    var searchVisible by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    /** 清空前的快照，供「撤销」恢复。 */
    private var clearedSnapshot: TextFieldValue? = null

    /**
     * 有未保存改动：代码框内容，或文件名（规范化后）跟上次落盘的不一样。
     * 保存成功后两个基准都跟着挪，再按返回不会再问一遍。
     */
    val hasChanges: Boolean
        get() = code.text != savedCode || repository.normalizeName(fileName) != savedName

    fun updateFileName(value: String) {
        fileName = value
    }

    fun updateCode(value: TextFieldValue) {
        code = value
    }

    fun requestExit() {
        exitDialogVisible = true
    }

    fun dismissExitDialog() {
        exitDialogVisible = false
    }

    /** 校验 → 写入。文件名不合法、重名或写入失败时返回原因，由页面提示。 */
    fun save(): SaveResult {
        val name = repository.normalizeName(fileName) ?: return SaveResult.Failure("请输入文件名")
        if (repository.isNameTaken(name, savedName)) return SaveResult.Failure("文件已存在")
        if (!repository.write(name, code.text)) return SaveResult.Failure("保存失败")
        // 落盘成功才挪基准，失败要保留改动，还能再试一次。
        savedCode = code.text
        savedName = name
        // 补全后缀后的真实文件名回填到输入框，和磁盘上的一致。
        fileName = name
        return SaveResult.Success(name)
    }

    fun selectAll() {
        code = code.copy(selection = TextRange(0, code.text.length))
    }

    fun clearCode() {
        clearedSnapshot = code
        code = TextFieldValue("")
    }

    fun undoClear() {
        clearedSnapshot?.let { code = it }
        clearedSnapshot = null
    }

    /** 粘贴：有选中则替换，否则插入光标处。 */
    fun insertText(text: String) {
        val selection = code.selection
        val newText = code.text.replaceRange(selection.min, selection.max, text)
        code = TextFieldValue(newText, TextRange(selection.min + text.length))
    }

    fun openSearch() {
        searchVisible = true
    }

    fun closeSearch() {
        searchVisible = false
        searchQuery = ""
        code = code.copy(selection = TextRange.Zero)
    }

    fun updateSearchQuery(value: String) {
        searchQuery = value
    }

    fun findNext() = moveToMatch(forward = true)

    fun findPrevious() = moveToMatch(forward = false)

    /** 循环查找：命中即选中；不做高亮着色。 */
    private fun moveToMatch(forward: Boolean) {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        val matches = findAllIndices(code.text, query)
        if (matches.isEmpty()) return

        val selection = code.selection
        val target = if (forward) {
            matches.firstOrNull { it.start >= selection.end } ?: matches.first()
        } else {
            matches.lastOrNull { it.start < selection.start } ?: matches.last()
        }
        code = code.copy(selection = TextRange(target.first, target.last + 1))
    }

    private fun findAllIndices(text: String, query: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            result += index until index + query.length
            index = text.indexOf(query, index + 1, ignoreCase = true)
        }
        return result
    }
}
