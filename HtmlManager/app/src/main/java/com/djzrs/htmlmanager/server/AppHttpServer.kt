package com.djzrs.htmlmanager.server

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder

/**
 * 只读的本地文件服务器：
 * `/` 渲染导航页，`/files/{name}` 返回 htmls 下的单个 html，其余 404。
 */
class AppHttpServer(
    private val htmlDir: File,
    port: Int = PORT
) : NanoHTTPD(BIND_ADDRESS, port) {

    override fun serve(session: IHTTPSession): Response = when (session.uri) {
        "/" -> newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            buildIndexPage()
        )

        else -> serveFile(session.uri)
    }

    private fun serveFile(uri: String): Response {
        val prefix = "/files/"
        if (!uri.startsWith(prefix)) return notFound()

        val name = uri.removePrefix(prefix)
        // File(name).name != name 说明带了路径分隔或 ..，直接拒绝，防目录穿越。
        if (name != File(name).name) return notFound()
        if (!name.endsWith(".html", ignoreCase = true)) return notFound()

        val file = File(htmlDir, name)
        if (!file.isFile) return notFound()

        val bytes = file.readBytes()
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "404 Not Found")

    private fun buildIndexPage(): String {
        val files = htmlDir.listFiles { file -> file.isFile && file.name.endsWith(".html", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        val items = if (files.isEmpty()) {
            "<li>暂无文件</li>"
        } else {
            files.joinToString(separator = "\n") { file ->
                val href = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
                "<li><a href=\"/files/$href\">${escapeHtml(file.name)}</a></li>"
            }
        }

        return """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>HTML 管家</title>
            <style>body{font-family:system-ui;background:#F8F9FA;margin:0;padding:24px;color:#202124}
            h1{font-size:22px;color:#1A73E8}ul{list-style:none;padding:0}
            li{margin:8px 0}a{display:block;padding:14px 16px;background:#fff;border-radius:16px;
            color:#202124;text-decoration:none;box-shadow:0 1px 3px rgba(0,0,0,.08)}</style>
            </head><body><h1>HTML 管家</h1><ul>
            $items
            </ul></body></html>
        """.trimIndent()
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    companion object {
        const val PORT = 8080
        private const val BIND_ADDRESS = "0.0.0.0"
    }
}
