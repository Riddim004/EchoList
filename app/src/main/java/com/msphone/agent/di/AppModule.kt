package com.msphone.agent.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.msphone.agent.BuildConfig
import com.msphone.agent.agent.harness.ToolRegistry
import com.msphone.agent.agent.llm.GlmApi
import com.msphone.agent.agent.llm.GlmLlmClient
import com.msphone.agent.agent.llm.LlmClient
import com.msphone.agent.agent.tools.CompleteTaskTool
import com.msphone.agent.agent.tools.CreateTaskTool
import com.msphone.agent.agent.tools.DeleteTaskTool
import com.msphone.agent.agent.tools.QueryTasksTool
import com.msphone.agent.agent.tools.SetReminderTool
import com.msphone.agent.agent.tools.UpdateTaskTool
import com.msphone.agent.data.local.AppDatabase
import com.msphone.agent.data.local.AiHistoryDao
import com.msphone.agent.data.local.ChatMessageDao
import com.msphone.agent.data.local.TaskDao
import com.msphone.agent.data.repository.AiHistoryRepositoryImpl
import com.msphone.agent.data.repository.ChatRepositoryImpl
import com.msphone.agent.data.repository.TaskRepositoryImpl
import com.msphone.agent.domain.repository.AiHistoryRepository
import com.msphone.agent.domain.repository.ChatRepository
import com.msphone.agent.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** v1→v2：新增聊天记录表（多轮上下文持久化），不影响已有任务数据 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`isError` INTEGER NOT NULL, " +
                    "`taskId` INTEGER, " +
                    "`draftJson` TEXT, " +
                    "`undone` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    /** v2→v3：新增 AI 解析历史表（回溯任务来源/评估解析质量），不影响已有数据 */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ai_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`rawInput` TEXT NOT NULL, " +
                    "`sourceType` TEXT NOT NULL, " +
                    "`resultType` TEXT NOT NULL, " +
                    "`resultSummary` TEXT NOT NULL, " +
                    "`isSuccess` INTEGER NOT NULL, " +
                    "`costMillis` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ms_phone_agent.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun provideAiHistoryDao(db: AppDatabase): AiHistoryDao = db.aiHistoryDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true // 保证 type="function" 等默认值被序列化
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.GLM_API_KEY}")
                    .build()
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideGlmApi(client: OkHttpClient, json: Json): GlmApi = Retrofit.Builder()
        .baseUrl(BuildConfig.GLM_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GlmApi::class.java)

    /** 预定义工具集注册（工具注册制：新增工具在此追加即可） */
    @Provides
    @Singleton
    fun provideToolRegistry(
        createTask: CreateTaskTool,
        queryTasks: QueryTasksTool,
        completeTask: CompleteTaskTool,
        setReminder: SetReminderTool,
        deleteTask: DeleteTaskTool,
        updateTask: UpdateTaskTool,
    ): ToolRegistry = ToolRegistry().apply {
        register(createTask)
        register(queryTasks)
        register(completeTask)
        register(setReminder)
        register(deleteTask)
        register(updateTask)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    abstract fun bindAiHistoryRepository(impl: AiHistoryRepositoryImpl): AiHistoryRepository

    @Binds
    abstract fun bindLlmClient(impl: GlmLlmClient): LlmClient
}
