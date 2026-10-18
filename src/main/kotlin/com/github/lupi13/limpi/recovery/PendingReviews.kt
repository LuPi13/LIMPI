package com.github.lupi13.limpi.recovery

import com.github.lupi13.limpi.storage.StateStore
import com.github.lupi13.limpi.storage.YamlFiles
import java.util.UUID

data class PendingReview(val owner: UUID?, val name: String, val kind: String, val id: String,
    val state: String, val reason: String, val command: String)

/** Read-only discovery also keeps malformed frozen payloads visible to administrators. */
object PendingReviews {
    fun list(store: StateStore): List<PendingReview> {
        val names = mutableMapOf<UUID, String>()
        return KINDS.flatMap { (prefix, kind) ->
            store.documents("$prefix/").map { (key, payload) ->
                val parts = key.split('/')
                val owner = parts.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val id = parts.getOrNull(2) ?: "?"
                val data = runCatching { YamlFiles.parse(payload) }.getOrNull()
                val identityValid = parts.size == 3 && owner != null && runCatching { UUID.fromString(id) }.isSuccess &&
                    (data?.get("owner") == null || data.get("owner") == owner.toString()) &&
                    (data?.get("id") == null || data.get("id") == id)
                val malformed = data == null || !identityValid
                val state = if (malformed) "MALFORMED" else data!!.getString("state") ?: "UNKNOWN"
                val reason = if (malformed) "문서 형식·식별자 오류: 원본 보존 후 개별 조회 필요" else
                    data!!.getString("reviewReason") ?: data.getString("reason") ?: when (state) {
                        "APPLYING" -> "물리 저장 확인 대기(진행 중일 수 있음)"
                        "OPEN", "CLOSING" -> "상자 창 저장 확인 대기(열려 있을 수 있음)"
                        "PENDING" -> "미수령 물품"
                        else -> "개별 기록 확인 필요"
                    }
                val name = owner?.let { uuid -> names.getOrPut(uuid) {
                    runCatching { YamlFiles.parse(store.get("player/$uuid")).getString("name") }.getOrNull() ?: "이름 없음"
                } } ?: "소유자 형식 오류"
                val command = if (!identityValid) "해결 예시: DB 백업 후 식별자를 확인하세요($key)." else when (kind) {
                    "market" -> "/limpi escrow resolve $owner $id removed|retained <근거>"
                    "delivery" -> if (state == "PENDING") "/financial claim (소유자 접속 후)" else
                        "/limpi delivery resolve $owner $id delivered|retry <근거>"
                    "removal" -> "/limpi removal resolve $owner $id removed|retained <근거>"
                    "chest" -> "/limpi chest resolve $owner $id before|after <근거>"
                    else -> "/limpi transition resolve $owner $id applied|retained <근거>"
                }
                PendingReview(owner, name, kind, id, state, reason, command)
            }
        }.sortedWith(compareBy({ it.owner?.toString().orEmpty() }, { it.kind }, { it.id }))
    }
    private val KINDS = linkedMapOf("delivery_pending" to "delivery", "removal_pending" to "removal",
        "ability_transition_pending" to "transition", "market_escrow_pending" to "market",
        "chest_session" to "chest")
}
