package com.aichatnovel.app.di

import java.util.concurrent.atomic.AtomicLong

/**
 * 一次导入的归属 ID 来源。
 *
 * storyId / chapterId 必须**每次导入都不同**：否则两次不同的导入会落进同一个作品 / 章节，
 * 后一次就会把前一次的 Story、Chapter、Content 顶掉（这正是 M2 的一部分）。
 * 生产实现给出真唯一 ID；测试注入确定值，避免断言依赖随机数。
 */
interface ImportIdGenerator {

    fun newStoryId(): String

    fun newChapterId(): String
}

/**
 * 生产实现：时间戳 + 进程内单调计数器。
 *
 * 保持可读（便于在 prompt / 日志里辨认），且在同一进程内不会重复。
 */
class SequentialImportIdGenerator(
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ImportIdGenerator {

    private val counter = AtomicLong(0L)

    override fun newStoryId(): String = newId("story")

    override fun newChapterId(): String = newId("chapter")

    private fun newId(prefix: String): String = "$prefix-${clockMillis()}-${counter.incrementAndGet()}"
}
