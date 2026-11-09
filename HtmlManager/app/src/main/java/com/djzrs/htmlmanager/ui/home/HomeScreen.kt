package com.djzrs.htmlmanager.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.djzrs.htmlmanager.data.FileRepository
import com.djzrs.htmlmanager.data.HtmlFile
import com.djzrs.htmlmanager.ui.theme.ServerRunningDot

private const val FILE_PROVIDER_AUTHORITY = "com.djzrs.htmlmanager.fileprovider"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isServerRunning: Boolean,
    onCreate: () -> Unit,
    onEdit: (name: String) -> Unit,
    onOpenServer: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // onResume 刷新列表：从编辑页 / 外部浏览器回来时名称与顺序都可能变化。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (viewModel.searchActive) {
                        OutlinedTextField(
                            value = viewModel.query,
                            onValueChange = viewModel::updateQuery,
                            singleLine = true,
                            placeholder = { Text("搜索文件名") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("HTML 管家", fontWeight = FontWeight.Medium)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            imageVector = if (viewModel.searchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (viewModel.searchActive) "关闭搜索" else "搜索"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            HomeBottomBar(
                isServerRunning = isServerRunning,
                onCreate = onCreate,
                onOpenServer = onOpenServer
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val files = viewModel.visibleFiles
            if (files.isEmpty()) {
                Text(
                    text = if (viewModel.files.isEmpty()) "请添加文件。" else "未找到匹配文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // 每项一张卡：白底 + 描边 + 圆角 + 间隙，和背景的浅灰拉开层次。
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.name }) { file ->
                        FileCard(
                            file = file,
                            onEdit = { onEdit(file.name) },
                            onPreview = { previewInBrowser(context, file.name) },
                            onDelete = { viewModel.askDelete(file) }
                        )
                    }
                }
            }
        }
    }

    viewModel.pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("删除文件") },
            text = { Text("确定删除 ${file.name}？") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FileCard(
    file: HtmlFile,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onPreview) {
                Icon(Icons.Outlined.Visibility, contentDescription = "预览")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun HomeBottomBar(
    isServerRunning: Boolean,
    onCreate: () -> Unit,
    onOpenServer: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCreate,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "新建文件", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = onOpenServer,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isServerRunning) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(ServerRunningDot, CircleShape)
                    )
                } else {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = if (isServerRunning) "服务器（运行中）" else "服务器",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** 预览：只发 content:// Intent 给外部浏览器，不做 WebView 兜底。 */
private fun previewInBrowser(context: Context, name: String) {
    val file = FileRepository(context).fileOf(name)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "text/html")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (intent.resolveActivity(context.packageManager) == null) {
        Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    } else {
        context.startActivity(intent)
    }
}
