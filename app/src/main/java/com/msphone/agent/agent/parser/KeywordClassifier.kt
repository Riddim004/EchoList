package com.msphone.agent.agent.parser

import com.msphone.agent.domain.model.TaskCategory

/** 离线降级用的关键词分类器（主通道由 GLM 分类） */
object KeywordClassifier {

    private val workKeywords = listOf(
        "会议", "开会", "汇报", "项目", "客户", "加班", "面试", "评审",
        "需求", "上线", "部署", "周报", "日报", "述职", "出差", "答辩",
        "例会", "提测", "代码", "方案", "合同", "培训", "邮件", "复盘",
    )

    fun classify(text: String): TaskCategory =
        if (workKeywords.any { it in text }) TaskCategory.WORK else TaskCategory.LIFE
}
