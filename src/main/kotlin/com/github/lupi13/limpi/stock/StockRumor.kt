package com.github.lupi13.limpi.stock

import org.bukkit.configuration.file.FileConfiguration
import java.util.Random

/**
 * A rumor told a minute before the prices move. Whether it holds is settled when it is told and kept with the prices,
 * so a restart in that minute changes nothing. [rises] may go against [good]: then the rumor was false.
 */
data class StockRumor(val code: String, val good: Boolean, val rises: Boolean, val percent: Int) {
    init { require(percent > 0) }

    /**
     * Log move of the base price. Up and down are the same size in log, so rumors do not sink a stock over time:
     * 40 is +40% going up and -28.6% going down.
     */
    val jump: Double get() = Math.log(1 + percent / 100.0) * if (rises) 1 else -1

    fun write(data: FileConfiguration) {
        data.set("$PATH.code", code); data.set("$PATH.good", good); data.set("$PATH.rises", rises); data.set("$PATH.percent", percent)
    }

    companion object {
        private const val PATH = "_meta.rumor"

        /** A damaged rumor is simply no rumor: no money hangs on it until it moves a price. */
        fun read(data: FileConfiguration): StockRumor? {
            val code = data.get("$PATH.code") as? String ?: return null
            val good = data.get("$PATH.good") as? Boolean ?: return null
            val rises = data.get("$PATH.rises") as? Boolean ?: return null
            val percent = (data.get("$PATH.percent") as? Int)?.takeIf { it > 0 } ?: return null
            return StockRumor(code, good, rises, percent)
        }

        fun clear(data: FileConfiguration) = data.set(PATH, null)
    }
}

data class StockRumorPolicy(val chancePercent: Int, val truthPercent: Int, val minPercent: Int, val maxPercent: Int) {
    init {
        require(chancePercent in 0..100 && truthPercent in 0..100)
        require(minPercent in 1..maxPercent && maxPercent <= 1000)
    }

    fun roll(codes: List<String>, random: Random): StockRumor? {
        if (codes.isEmpty() || random.nextInt(100) >= chancePercent) return null
        val good = random.nextBoolean()
        val holds = random.nextInt(100) < truthPercent
        return StockRumor(codes[random.nextInt(codes.size)], good, good == holds, minPercent + random.nextInt(maxPercent - minPercent + 1))
    }
}

/** `{name}` is followed by no particle, since a stock's name can end in anything. */
object StockRumorText {
    private const val NAME = "{name}"

    val GOOD = listOf(
        "{name} 사장이 출근길에 다이아몬드 64개를 주웠다는 소문이 돕니다.",
        "{name} 구내식당 제육볶음이 미슐랭 별을 받았다는 소문이 돕니다.",
        "{name} 창고 바닥을 파 보니 고대 잔해가 나왔다는 제보가 있습니다.",
        "{name} 회장 꿈에 돼지 세 마리가 나왔다고 합니다.",
        "{name} 인턴이 실수로 엑셀을 고쳤더니 매출이 세 배가 되었다고 합니다.",
        "주민 네 명이 {name} 주식을 에메랄드로 사겠다고 줄을 섰다고 합니다.",
        "엔더 드래곤이 {name} 제품만 쓴다고 인터뷰했다는 소문입니다.",
        "{name} 신제품을 먹은 크리퍼가 터지지 않고 웃었다고 합니다.",
        "{name} 금고 구석에서 잊고 있던 황금 사과가 나왔다고 합니다.",
        "옆집 점쟁이 말로는 {name} 올해 운세가 대길이랍니다.",
        "{name} 사장이 잃어버린 지갑을 찾았는데 돈이 더 들어 있었다고 합니다.",
        "{name} 경비 아저씨가 요즘 콧노래를 부른다는 고급 정보입니다.",
        "{name} 화장실 휴지가 두 겹으로 바뀌었다고 합니다. 여유가 있다는 뜻입니다.",
        "피글린들이 금괴 대신 {name} 주권을 받기 시작했다는 소문입니다.",
        "{name} 직원 전원이 야근 없이 퇴근했는데 실적은 그대로랍니다.",
        "길고양이가 {name} 본사 앞에서만 밥을 먹는다고 합니다. 길조입니다.",
    )

    val BAD = listOf(
        "{name} 사장이 회사 금고 비밀번호를 1234로 바꿨다는 소문이 돕니다.",
        "{name} 창고에 크리퍼가 들어갔는데 아직 아무도 못 찾았다고 합니다.",
        "{name} 경리 직원이 회삿돈을 크래시에 넣고 창을 닫았다고 합니다.",
        "{name} 회장이 양 경주에 회사를 걸었다는 소문입니다.",
        "{name} 신제품을 먹은 좀비가 배탈이 났다고 합니다.",
        "{name} 구내식당 제육볶음이 사실 썩은 살점이었다는 제보가 있습니다.",
        "{name} 사장이 용암 위에서 셀카를 찍다가 사라졌다고 합니다.",
        "{name} 본사 건물이 사실 흙집이었다고 합니다.",
        "{name} 인턴이 실수로 엑셀을 고쳤더니 매출이 사라졌다고 합니다.",
        "{name} 회계 장부가 사실 사장의 낙서장이었다는 폭로가 나왔습니다.",
        "{name} 서버실이 TNT 창고 옆으로 이사했다고 합니다.",
        "{name} 사장이 사무실에서 편의점 폐기를 몰래 먹다가 들켰다고 합니다.",
        "{name} 화장실 휴지가 한 겹으로 바뀌었다고 합니다. 심상치 않습니다.",
        "주민들이 {name} 주식은 썩은 살점 하나와도 안 바꿔 준다고 합니다.",
        "{name} 직원들이 점심시간마다 몰래 이력서를 쓴다고 합니다.",
        "옆집 점쟁이가 {name} 이야기를 듣더니 말없이 복채를 돌려줬다고 합니다.",
    )

    fun pick(good: Boolean, random: Random): String = (if (good) GOOD else BAD).let { it[random.nextInt(it.size)] }

    /** The words before and after the stock's name, for colouring it. */
    fun split(line: String): Pair<String, String> = line.substringBefore(NAME) to line.substringAfter(NAME)
}
