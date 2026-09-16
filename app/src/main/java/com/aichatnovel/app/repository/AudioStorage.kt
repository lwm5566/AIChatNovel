package com.aichatnovel.app.repository

/**
 * 音频文件的落地存储。
 *
 * 存在的意义：**provider 不决定业务层文件路径**。供应商只交出字节，
 * 由这里决定存哪、返回一个稳定引用。
 *
 * 本阶段只用 app 私有目录下的普通文件；不引入数据库 / DataStore / 云存储。
 */
interface AudioStorage {

    /** 写入音频，返回可交给播放器的稳定引用。同名文件应被覆盖。 */
    suspend fun save(fileName: String, bytes: ByteArray): String

    suspend fun delete(reference: String)

    suspend fun deleteAll()
}

/**
 * 从真实音频里读出时长。
 *
 * 这是 [com.aichatnovel.app.domain.model.DurationSource.Audio] 的唯一合法来源：
 * 读不出来就必须返回 null（调用方据此判定失败），**绝不允许回退到估算值再标成 Audio**。
 */
interface AudioDurationProbe {

    /** @return 毫秒时长；无法确定时返回 null。 */
    suspend fun probe(reference: String): Long?
}
