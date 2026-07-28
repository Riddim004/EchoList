package com.msphone.agent.domain.repository

import com.msphone.agent.domain.model.AiHistoryRecord
import kotlinx.coroutines.flow.Flow

/** AI 解析历史仓库：埋点写入 + 后续设置页展示预留 */
interface AiHistoryRepository {

    /** 记录一次解析结果并裁剪超量旧记录；失败静默，不影响主流程 */
    suspend fun record(record: AiHistoryRecord)

    /** 最近 N 条解析历史（倒序），供后续 UI 展示 */
    fun observeRecent(limit: Int = 100): Flow<List<AiHistoryRecord>>
}
