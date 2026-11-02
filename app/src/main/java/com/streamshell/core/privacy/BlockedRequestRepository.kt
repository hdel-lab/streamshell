package com.streamshell.core.privacy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One refused link. Holds the HOST only, never a full URL, query, title or account data. */
data class BlockedRequest(
    val host: String,
    val rule: String,
    /** List id ("builtin", a subscription id) or "custom"; null when unknown. */
    val source: String?,
    val timestampMs: Long
)

/**
 * Local, in-memory diagnostics (cleared on app restart or on demand; nothing is written to disk).
 *  - [blockedCount]: links StreamShell actually refused to open.
 *  - [observedCount] / [observedHosts]: requests the official player made, which StreamShell only WATCHES.
 *    They are never blocked and are not an estimate of all device traffic.
 */
interface BlockedRequestRepository {
    val observedCount: StateFlow<Long>
    val observedHosts: StateFlow<Map<String, Int>>
    val blockedCount: StateFlow<Long>
    val recent: StateFlow<List<BlockedRequest>>
    fun recordObserved(host: String?)
    fun recordBlocked(request: BlockedRequest)
    fun clear()
}

class InMemoryBlockedRequestRepository(
    private val capacity: Int = 200,
    private val maxHosts: Int = 100
) : BlockedRequestRepository {
    private val observed = MutableStateFlow(0L)
    private val hosts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val blocked = MutableStateFlow(0L)
    private val log = MutableStateFlow<List<BlockedRequest>>(emptyList())

    override val observedCount: StateFlow<Long> = observed.asStateFlow()
    override val observedHosts: StateFlow<Map<String, Int>> = hosts.asStateFlow()
    override val blockedCount: StateFlow<Long> = blocked.asStateFlow()
    override val recent: StateFlow<List<BlockedRequest>> = log.asStateFlow()

    override fun recordObserved(host: String?) {
        observed.update { it + 1 }
        if (host != null) {
            hosts.update { m ->
                if (host in m || m.size < maxHosts) m + (host to (m[host] ?: 0) + 1) else m
            }
        }
    }

    override fun recordBlocked(request: BlockedRequest) {
        blocked.update { it + 1 }
        log.update { (listOf(request) + it).take(capacity) }
    }

    override fun clear() {
        observed.value = 0
        hosts.value = emptyMap()
        blocked.value = 0
        log.value = emptyList()
    }
}
