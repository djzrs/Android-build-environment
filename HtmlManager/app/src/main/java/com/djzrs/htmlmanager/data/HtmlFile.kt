package com.djzrs.htmlmanager.data

/** 首页列表项：只暴露文件名与修改时间，避免把 File 泄给 UI。 */
data class HtmlFile(
    val name: String,
    val lastModified: Long
)
