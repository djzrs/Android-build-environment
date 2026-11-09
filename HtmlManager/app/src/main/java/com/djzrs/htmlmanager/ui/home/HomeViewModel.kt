package com.djzrs.htmlmanager.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.djzrs.htmlmanager.data.FileRepository
import com.djzrs.htmlmanager.data.HtmlFile

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)

    var files by mutableStateOf<List<HtmlFile>>(emptyList())
        private set

    var query by mutableStateOf("")
        private set

    var searchActive by mutableStateOf(false)
        private set

    var pendingDelete by mutableStateOf<HtmlFile?>(null)
        private set

    /** Snackbar 文案，展示后由 UI 调用 [consumeMessage] 清空。 */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    /** 搜索不区分大小写；关闭搜索时恢复全量。 */
    val visibleFiles: List<HtmlFile>
        get() = if (query.isBlank()) files
        else files.filter { it.name.contains(query.trim(), ignoreCase = true) }

    fun refresh() {
        files = repository.list()
    }

    fun toggleSearch() {
        searchActive = !searchActive
        if (!searchActive) query = ""
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun askDelete(file: HtmlFile) {
        pendingDelete = file
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete() {
        val name = pendingDelete?.name ?: return
        pendingDelete = null
        if (repository.delete(name)) {
            refresh()
            message = "已删除"
        } else {
            message = "删除失败"
        }
    }

    fun consumeMessage() {
        message = null
    }
}
