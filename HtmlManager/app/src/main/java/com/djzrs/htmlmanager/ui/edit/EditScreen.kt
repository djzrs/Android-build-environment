package com.djzrs.htmlmanager.ui.edit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.djzrs.htmlmanager.ui.theme.CodeTextStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    viewModel: EditViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    fun saveAndExit() {
        viewModel.dismissExitDialog()
        when (val result = viewModel.save()) {
            // 用 Toast 而不是 Snackbar：不用等它自然消失，退出后首页也还看得见。
            is SaveResult.Success -> {
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onExit()
            }

            is SaveResult.Failure -> scope.launch {
                snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    // 有修改先拦一道；无修改直接走。保存过的内容不算改动（基准在 ViewModel 里跟着落盘一起挪）。
    fun onBack() {
        if (viewModel.hasChanges) viewModel.requestExit() else onExit()
    }

    BackHandler { onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditTopBar(
                fileName = viewModel.fileName,
                onFileNameChange = viewModel::updateFileName,
                onBack = { onBack() },
                onSave = { saveAndExit() }
            )
        },
        bottomBar = {
            if (viewModel.searchVisible) {
                SearchBar(
                    query = viewModel.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    // 命中后把焦点交回代码框，选中文本才会滚动到可见位置。
                    onPrevious = {
                        viewModel.findPrevious()
                        focusRequester.requestFocus()
                    },
                    onNext = {
                        viewModel.findNext()
                        focusRequester.requestFocus()
                    },
                    onClose = viewModel::closeSearch
                )
            }
        },
        floatingActionButton = {
            EditorActions(
                onSelectAll = viewModel::selectAll,
                onClear = {
                    viewModel.clearCode()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "已清空",
                            actionLabel = "撤销"
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoClear()
                    }
                },
                onCopy = { copyToClipboard(context, viewModel) },
                onPaste = { pasteFromClipboard(context, viewModel) },
                onSearch = viewModel::openSearch
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CodeEditor(
                value = viewModel.code,
                onValueChange = viewModel::updateCode,
                focusRequester = focusRequester,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 64.dp, top = 8.dp, bottom = 8.dp)
            )
        }
    }

    if (viewModel.exitDialogVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExitDialog,
            title = { Text("有未保存的改动") },
            text = { Text("文件名或内容改过，是否保存？") },
            confirmButton = {
                TextButton(onClick = { saveAndExit() }) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.dismissExitDialog()
                        onExit()
                    }) { Text("放弃修改") }
                    TextButton(onClick = viewModel::dismissExitDialog) { Text("继续编辑") }
                }
            }
        )
    }
}

@Composable
private fun EditTopBar(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 和系统返回、手势返回走同一条路径：有改动先问，没改动直接走。
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                singleLine = true,
                placeholder = { Text("文件名") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onSave,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // 代码框和页面背景都是浅色，光靠色差看不出边界，所以描一圈边：聚焦时用主色，未聚焦用弱描边。
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "codeBorder"
    )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (focused) 2.dp else 1.dp, borderColor)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = CodeTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
        )
    }
}

@Composable
private fun EditorActions(
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        ActionFab(Icons.Outlined.SelectAll, "全选", onSelectAll)
        ActionFab(Icons.Outlined.DeleteSweep, "清空", onClear)
        ActionFab(Icons.Outlined.ContentCopy, "复制", onCopy)
        ActionFab(Icons.Outlined.ContentPaste, "粘贴", onPaste)
        ActionFab(Icons.Outlined.Search, "搜索", onSearch)
    }
}

@Composable
private fun ActionFab(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("搜索代码") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一个")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一个")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
            }
        }
    }
}

private fun clipboard(context: Context): ClipboardManager? =
    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

private fun copyToClipboard(context: Context, viewModel: EditViewModel) {
    val selection = viewModel.code.selection
    val text = if (selection.collapsed) viewModel.code.text
    else viewModel.code.text.substring(selection.min, selection.max)

    clipboard(context)?.setPrimaryClip(ClipData.newPlainText("HTML", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun pasteFromClipboard(context: Context, viewModel: EditViewModel) {
    val text = clipboard(context)
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()

    if (text.isNullOrEmpty()) {
        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
        return
    }
    viewModel.insertText(text)
}
