package com.msphone.agent.data.repository

import com.msphone.agent.data.local.AiHistoryDao
import com.msphone.agent.data.local.AiHistoryEntity
import com.msphone.agent.domain.model.AiHistoryRecord
import com.msphone.agent.domain.model.AiResultType
import com.msphone.agent.domain.model.AiSourceType
import com.msphone.agent.domain.repository.AiHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiHistoryRepositoryImpl @Inject constructor(
    private val dao: AiHistoryDao,
) : AiHistoryRepository {

    override suspend fun record(record: AiHistoryRecord) {
        // 埋点写入失败绝不能影响聊天主流程
        runCatching {
            dao.insert(record.toEntity())
            dao.trim()
        }
    }

    override fun observeRecent(limit: Int): Flow<List<AiHistoryRecord>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    // ---------- mapping ----------

    private fun AiHistoryRecord.toEntity(): AiHistoryEntity = AiHistoryEntity(
        id = id,
        rawInput = rawInput,
        sourceType = sourceType.name,
        resultType = resultType.name,
        resultSummary = resultSummary.take(500),
        isSuccess = isSuccess,
        costMillis = costMillis,
        createdAt = createdAt,
    )

    private fun AiHistoryEntity.toDomain(): AiHistoryRecord = AiHistoryRecord(
        id = id,
        rawInput = rawInput,
        sourceType = runCatching { AiSourceType.valueOf(sourceType) }.getOrDefault(AiSourceType.LLM),
        resultType = runCatching { AiResultType.valueOf(resultType) }.getOrDefault(AiResultType.TEXT_REPLY),
        resultSummary = resultSummary,
        isSuccess = isSuccess,
        costMillis = costMillis,
        createdAt = createdAt,
    )
}
