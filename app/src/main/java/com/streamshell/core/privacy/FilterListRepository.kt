package com.streamshell.core.privacy

import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object Initializing : EngineState
    data class Ready(val engine: DomainFilterEngine, val ruleCount: Int, val sourceIds: List<String>) : EngineState
    data class Failed(val reason: String) : EngineState
}

sealed interface DownloadResult {
    data object NotModified : DownloadResult
    data class Data(val bytes: ByteArray, val etag: String?, val lastModified: String?) : DownloadResult
}

fun interface FilterListDownloader {
    /** https only; every hop (including redirects) must be in [allowedHosts]. Sends validators when given. */
    @Throws(IOException::class)
    fun download(url: String, allowedHosts: Set<String>, etag: String?, lastModified: String?): DownloadResult
}

/** Persistence seams so the repository has no Android dependency and is unit-testable. */
interface FilterMetaStore {
    suspend fun load(): String?
    suspend fun save(json: String)
}

interface FilterFileStore {
    fun read(id: String): ByteArray?

    /** Must replace the old copy atomically (write temp, then rename). */
    fun writeAtomic(id: String, bytes: ByteArray): Boolean
    fun delete(id: String)
}

data class UpdateSummary(val updated: Int, val notModified: Int, val failed: Int) {
    val anyFailed get() = failed > 0
}

sealed interface AddResult {
    data class Added(val id: String) : AddResult

    /** The download host is not trusted yet; ask the user, then call again with trustHost = true. */
    data class NeedsTrust(val host: String) : AddResult
    data class Invalid(val reason: String) : AddResult
}

interface FilterListRepository {
    val engineState: StateFlow<EngineState>
    val entries: StateFlow<List<ListEntry>>
    val trustedHosts: StateFlow<Set<String>>
    val updating: StateFlow<Boolean>

    /** Builds the engine from the built-in list plus CACHED lists. Never touches the network, so offline start works. */
    suspend fun initialize()

    /** Downloads enabled lists (or only [onlyIds]). Per list: a failure keeps that list's last-known-good copy. */
    suspend fun updateAll(onlyIds: Set<String>? = null): UpdateSummary
    suspend fun add(name: String, url: String, trustHost: Boolean = false): AddResult
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun remove(id: String)
    suspend fun resetToDefaults()
}

class DefaultFilterListRepository(
    private val builtin: () -> ByteArray,
    private val meta: FilterMetaStore,
    private val files: FilterFileStore,
    private val downloader: FilterListDownloader,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) : FilterListRepository {
    private val state = MutableStateFlow<EngineState>(EngineState.Initializing)
    override val engineState: StateFlow<EngineState> = state.asStateFlow()
    private val entriesFlow = MutableStateFlow<List<ListEntry>>(emptyList())
    override val entries: StateFlow<List<ListEntry>> = entriesFlow.asStateFlow()
    private val trusted = MutableStateFlow(FilterSources.defaultTrustedHosts)
    override val trustedHosts: StateFlow<Set<String>> = trusted.asStateFlow()
    private val updatingFlow = MutableStateFlow(false)
    override val updating: StateFlow<Boolean> = updatingFlow.asStateFlow()

    private val mutex = Mutex()
    private var config: FilterConfig = FilterConfig.defaults()
    private var loaded = false

    private suspend fun ensureLoaded() {
        if (loaded) return
        config = FilterConfigSerializer.decode(meta.load())
        loaded = true
    }

    private suspend fun commit() {
        meta.save(FilterConfigSerializer.encode(config))
        publish()
    }

    private fun publish() {
        trusted.value = config.trustedHosts
        entriesFlow.value = config.subscriptions.map { ListEntry(it, config.statuses[it.id] ?: ListStatus(), files.read(it.id) != null) }
    }

    override suspend fun initialize() = withContext(dispatcher) {
        mutex.withLock {
            ensureLoaded()
            publish()
            rebuild()
        }
    }

    /** Reads the built-in list and every enabled cached list. Disk content is untrusted and re-validated. */
    private fun rebuild() {
        try {
            val rules = ArrayList<DomainRule>()
            val used = ArrayList<String>()
            val b = FilterListValidator.validate(builtin())
            if (b !is FilterListValidator.Result.Valid) {
                state.value = EngineState.Failed("Built-in list invalid")
                return
            }
            rules += b.parsed.rules.map { it.copy(listId = FilterSources.BUILTIN_ID) }
            used += FilterSources.BUILTIN_ID
            for (sub in config.subscriptions.filter { it.enabled }) {
                val bytes = files.read(sub.id) ?: continue
                when (val r = FilterListValidator.validate(bytes)) {
                    is FilterListValidator.Result.Valid -> {
                        if (rules.size + r.parsed.rules.size > MAX_TOTAL_RULES) continue // resource limit: skip the list that would exceed it
                        rules += r.parsed.rules.map { it.copy(listId = sub.id) }
                        used += sub.id
                    }

                    is FilterListValidator.Result.Invalid -> files.delete(sub.id) // corrupt cache: drop it, never use it
                }
            }
            val engine = DomainFilterEngine(rules)
            state.value = EngineState.Ready(engine, engine.ruleCount, used)
        } catch (e: Exception) {
            state.value = EngineState.Failed(e.message ?: "Could not load filter lists")
        }
    }

    override suspend fun updateAll(onlyIds: Set<String>?): UpdateSummary = withContext(dispatcher) {
        mutex.withLock {
            ensureLoaded()
            updatingFlow.value = true
            try {
                var updated = 0
                var notModified = 0
                var failed = 0
                val statuses = config.statuses.toMutableMap()
                for (sub in config.subscriptions.filter { it.enabled && (onlyIds == null || it.id in onlyIds) }) {
                    val old = statuses[sub.id] ?: ListStatus()
                    val now = clock()
                    // Validators are only useful while a cached copy exists.
                    val useValidators = files.read(sub.id) != null
                    val result = try {
                        downloader.download(sub.url, config.trustedHosts, if (useValidators) old.etag else null, if (useValidators) old.lastModified else null)
                    } catch (e: Exception) {
                        statuses[sub.id] = old.copy(lastAttemptMs = now, error = e.message?.take(160) ?: "download failed")
                        failed++
                        continue
                    }
                    when (result) {
                        DownloadResult.NotModified -> {
                            statuses[sub.id] = old.copy(lastAttemptMs = now, lastSuccessMs = now, error = null)
                            notModified++
                        }

                        is DownloadResult.Data -> when (val v = FilterListValidator.validate(result.bytes)) {
                            is FilterListValidator.Result.Invalid -> {
                                statuses[sub.id] = old.copy(lastAttemptMs = now, error = v.reason) // last-known-good stays untouched
                                failed++
                            }

                            is FilterListValidator.Result.Valid ->
                                if (files.writeAtomic(sub.id, result.bytes)) {
                                    statuses[sub.id] = ListStatus(now, now, v.parsed.rules.size, v.parsed.skipped, null, result.etag, result.lastModified)
                                    updated++
                                } else {
                                    statuses[sub.id] = old.copy(lastAttemptMs = now, error = "Could not store the list")
                                    failed++
                                }
                        }
                    }
                }
                config = config.copy(statuses = statuses)
                commit()
                rebuild()
                UpdateSummary(updated, notModified, failed)
            } finally {
                updatingFlow.value = false
            }
        }
    }

    override suspend fun add(name: String, url: String, trustHost: Boolean): AddResult = withContext(dispatcher) {
        mutex.withLock {
            ensureLoaded()
            val cleanName = name.trim()
            val cleanUrl = url.trim()
            if (cleanName.isEmpty() || cleanName.length > 60) return@withLock AddResult.Invalid("Give the list a name (up to 60 characters).")
            if (cleanUrl.length > MAX_URL || !cleanUrl.startsWith("https://")) return@withLock AddResult.Invalid("Only https:// addresses are allowed.")
            val host = try {
                URI(cleanUrl).host?.lowercase()
            } catch (_: Exception) {
                null
            }
                ?: return@withLock AddResult.Invalid("That is not a valid address.")
            if (DomainInput.normalize(host) == null) return@withLock AddResult.Invalid("That is not a valid address.")
            if (config.subscriptions.any { it.url == cleanUrl }) return@withLock AddResult.Invalid("This list is already added.")
            if (config.subscriptions.size >= MAX_LISTS) return@withLock AddResult.Invalid("At most $MAX_LISTS lists.")
            if (host !in config.trustedHosts) {
                if (!trustHost) return@withLock AddResult.NeedsTrust(host)
                config = config.copy(trustedHosts = config.trustedHosts + host)
            }
            val id = "user-" + sha(cleanUrl).take(10)
            config = config.copy(subscriptions = config.subscriptions + Subscription(id, cleanName, cleanUrl, null, cleanUrl, enabled = true, recommended = false))
            commit()
            AddResult.Added(id)
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        withContext(dispatcher) {
            mutex.withLock {
                ensureLoaded()
                config = config.copy(subscriptions = config.subscriptions.map { if (it.id == id) it.copy(enabled = enabled) else it })
                commit()
                rebuild()
            }
        }
    }

    override suspend fun remove(id: String) {
        withContext(dispatcher) {
            mutex.withLock {
                ensureLoaded()
                val sub = config.subscriptions.firstOrNull { it.id == id } ?: return@withLock
                if (sub.recommended) return@withLock // recommended lists are disabled, not removed
                files.delete(id)
                config = config.copy(subscriptions = config.subscriptions - sub, statuses = config.statuses - id)
                commit()
                rebuild()
            }
        }
    }

    override suspend fun resetToDefaults() {
        withContext(dispatcher) {
            mutex.withLock {
                ensureLoaded()
                config.subscriptions.forEach { files.delete(it.id) }
                config = FilterConfig.defaults()
                commit()
                rebuild()
            }
        }
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_LISTS = 20
        const val MAX_URL = 500
        const val MAX_TOTAL_RULES = 400_000
    }
}
