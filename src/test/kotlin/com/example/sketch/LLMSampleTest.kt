package com.example.sketch

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import kotlin.test.Test

@SpringBootTest
class LLMSampleTest(
    @Autowired val webClient: WebClient,
) {
    val objectMapper = ObjectMapper()
    val defaultPrompt =
        "response GraphicCard info of next paragraph with JSON format.\n parameters only be 'price', 'model'.\n" +
            "this is sample json format." +
            "\"model\":\"RTX 4070 SUPER MIRACLE X3 WHITE D6X 12GB\", \"price\":475000\n" +
            "and if you cant find the gpu model then return null\n" +
            "and if you cant find the gpu price then return null\n" +
            "and if the price is not only gpu price but also some of mixed items price, then return null all parameters\n " +
            "paragraph:\n"

    @Test
    @DisplayName("게시글에 포함된 가격 840000원을 반환해야하며, 모델의 이름 일부를 반환해야 한다 ")
    fun ollamaTest1() {
        val requestBody = requestBody(item)
        val response = requestExecute(requestBody)
        val result = objectMapper.readTree(parseJsonString(response.body).get("response").textValue())
        println(result)
        assert((result.get("price").intValue() == 840000) || (result.get("price").textValue() == "840000"))
        assert(result.get("model").textValue().contains("RTX") && result.get("model").textValue().contains("4070"))
    }

    private fun requestBody(value: String) =
        mapOf(
            "model" to "llama3",
            "prompt" to defaultPrompt + value,
            "stream" to false,
            "format" to "json",
        )

    private fun requestExecute(requestBody: Map<String, Any>) =
        webClient
            .post()
            .uri("http://localhost:11434/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .toEntity(String::class.java)
            .block()
}

private const val item =
    " 이전글 다음글목록\n" +
        "[컴퓨터] PC 주요부품 \n" +
        "이엠텍 지포스 RTX 4070 SUPER MIRACLE X3 WHITE D6X 12GB 미개봉\n" +
        "프로필 사진\n" +
        "YongWoo1\n" +
        "본인인증 회원 \n" +
        "구매문의\n" +
        "2024.06.13. 20:59조회 17\n" +
        "댓글 0URL 복사\n" +
        "상품이미지\n" +
        "디지털/가전 > PC부품 > 그래픽카드\n" +
        "판매(안전) 이엠텍 지포스 RTX 4070 SUPER MIRACLE X3 WHITE D6X 12GB 미개봉\n" +
        "\n" +
        "840,000원\n" +
        "상품 상태\n" +
        "미개봉\n" +
        "결제 방법\n" +
        "네이버페이 송금, 네이버페이 안전결제 \n" +
        "(무통장, 계좌간편결제 중 선택 가능하며 구매자가\n" +
        "수수료 부담) 기타 결제 방식은 판매자와 협의\n" +
        "\n" +
        "배송 방법\n" +
        "택배 거래, 직거래\n" +
        "판매자\n" +
        "dy******@naver.com 연락처 보기 \n" +
        "본인인증 완료\n" +
        "\n" +
        "거래 후기1건0건0건\n" +
        "\n" +
        "구매 문의\n" +
        "구매 문의 채팅\n" +
        "\n" +
        "N pay\n" +
        "안전결제\n" +
        "\n" +
        "N pay\n" +
        "무료 송금\n" +
        "네이버페이 송금은 에스크로 기능이 제공되지 않으며, 판매자에게 결제금액이 바로 전달되는 ‘일반결제'입니다. \n" +
        "\n" +
        "직접결제 시 아래 사항에 유의해주세요.\n" +
        "카페 구매문의 채팅이나 전화 등을 이용해 연락하고 외부 메신저 이용 및 개인 정보 유출에 주의하세요.\n" +
        "계좌이체 시 선입금을 유도할 경우 안전한 거래인지 다시 한번 확인하세요.\n" +
        "불확실한 판매자(본인 미인증, 해외IP, 사기의심 전화번호)의 물건은 구매하지 말아주세요.\n" +
        "\n" +
        "네이버에 등록된 판매 물품과 내용은 개별 판매자가 등록한 것으로서, 네이버카페는 등록을 위한 시스템만 제공하며 내용에 대하여 일체의 책임을 지지 않습니다.\n" +
        "\n" +
        "\uD83D\uDCA5파격할인! 로봇청소기\uD83E\uDD16 8만원 대 ▶https://tracking.joongna.com/dh1\n" +
        "\uD83D\uDCF2 중고나라 앱 다운받기 ▶https://tracking.joongna.com/a1\n" +
        "거래 전! 카카오톡 간편 사기 조회 ▶https://tracking.joongna.com/kat\n" +
        "───────────────────────\n" +
        "※ 게시글 수집 및 이용 안내 확인 ▶ https://web.joongna.com/post-policy\n" +
        "※ 카페 상품 게시글은 자동으로 중고나라 앱/사이트에 노출합니다. 노출을 원하지 않으실 경우 고객센터로 문의 바랍니다.\n" +
        "※ 중고나라는 통신판매의 당사자가 아니며 상품정보, 거래에 관한 의무와 책임은 각 판매자에게 있습니다.\n" +
        "※ 중고나라 이용정책을 반드시 확인해 주세요. ▶ https://cafe.naver.com/joonggonara/998699127\n" +
        "\n" +
        "... 더보기\n" +
        "※ 카페 상품 게시글은 자동으로 중고나라 앱/사이트에 노출합니다. 노출을 원하지 않으실 경우 고객센터로 문의 바랍니다. \n" +
        "\n" +
        "※ 등록한 게시글이 회원의 신고를 받거나 이상거래로 모니터링 될 경우 중고나라 사기통합조회 DB로 수집/활용될 수 있습니다. \n" +
        "\n" +
        "─────────────────── \n" +
        "\n" +
        "\uD83D\uDCE2 제목에 \"제조사/ 브랜드 명\"과 \"상품명(ex. 맥북 에어 13)”를 넣어 작성하면, 보다 빠른 판매가 가능합니다!  \n" +
        "\n" +
        "\uD83D\uDCE2 게시글 작성 시 배송 방법에 “직거래”와 “내 위치” 설정할 경우, 보다 빠른 판매가 가능합니다! \n" +
        "\n" +
        "\n" +
        "010 2508 8058 문자주세요 수량 1개 있습니다.\n" +
        "\n" +
        "미개봉 제품이고 안전페이 택배거래합니다.\n" +
        "\n" +
        "내일 배송 해드릴게요! \n" +
        "\n" +
        "일반택배거래도 가능합니다.\n" +
        "\n" +
        "오픈마켓에서 구매해서 영수증 있습니다.\n" +
        "\n" +
        "직거래는 토요일 오후 신림 금천구 구로디지털 에서 직거래 합니다.\n" +
        "\n" +
        "\n" +
        "구매 문의\n" +
        "구매 문의 채팅\n" +
        "\n" +
        "N pay\n" +
        "안전결제\n" +
        "\n" +
        "N pay\n" +
        "무료 송금\n" +
        "주의하세요!\n" +
        "\n" +
        "외부 메신저를 통해 결제 링크를 전달하거나 현금 결제를 유도할 경우\n" +
        "위험 거래일 가능성이 높으니 절대 주의하세요.\n" +
        "\n" +
        "프로필 사진YongWoo1님의 게시글 더보기 \n" +
        " 댓글0\n" +
        " 공유\n" +
        "신고\n" +
        "클린봇이 악성 댓글을 감지합니다.\n" +
        "설정\n" +
        "댓글관심글 댓글 알림\n" +
        "등록\n" +
        "댓글을 입력하세요\n" +
        "블루뽀이\n" +
        "댓글을 남겨보세요\n" +
        "선택된 파일 없음등록\n" +
        " 글쓰기목록 TOP\n" +
        "'rtx 4070' 검색결과\n" +
        "이 카페 글\n" +
        "이 키워드 새글 구독하기\n" +
        "등록\n" +
        "2024 ASUS ROG 제피러스 신형 G14 + 윈도우정품 설치모델 (GA403UI-QS091 / Ryzen9 8945hs / RTX 4070 / RAM 32GB)\t에라토\t21:23\n" +
        "이엠텍 지포스 RTX 4070 SUPER MIRACLE X3 WHITE D6X 12GB 미개봉\tYongWoo1\t20:15\n" +
        "[앱 상품]rtx 4070 게이밍프로 새제품급 그래픽카드\t컴또리\t19:42\n" +
        "[앱 새상품]보증1년 스트릭스 18인치 G18 Rtx4070\t스도어\t19:32\n" +
        "[앱 새상품]아수스 18인치 g18 Rtx4070 보증1년\t스도어\t19:29\n" +
        "페이징 이동12345\n" +
        "전체 카페 글\n" +
        "ASUS DUAL 지포스 RTX 4070 SUPER D6X 12GB 43% 핫딜\t\t2024.06.11.\n" +
        "RTX4070에 맞는 견적이 고민입니다.\t\t2024.04.22.\n" +
        "COLORFUL 지포스 RTX 4070 토마호크 EX D6X 12GB 43% 핫딜댓글[1]\t\t2024.06.11.\n" +
        "MSI 지포스 RTX 4070 SUPER 벤투스 2X OC D6X 12GB 43% 핫딜\t\t2024.06.11.\n" +
        "GIGABYTE 지포스 RTX 4070 SUPER AERO OC D6X 12GB 제이씨현 43% 핫딜\t\t2024.06.11.\n" +
        "페이징 이동12345\n" +
        "레이어 닫기"
const val item2 =
    " 이전글 다음글목록\n" +
        "[컴퓨터] PC 주요부품 \n" +
        "갤럭시 GALAX 지포스 RTX 3060 EX WHITE OC V2 D6 12GB\n" +
        "프로필 사진\n" +
        "발냄새노\n" +
        "중고나라 회원 \n" +
        "구매문의\n" +
        "2024.06.13. 19:57조회 18\n" +
        "댓글 0URL 복사\n" +
        "상품이미지\n" +
        "디지털/가전 > PC부품 > 그래픽카드\n" +
        "판매 갤럭시 GALAX 지포스 RTX 3060 EX WHITE OC V2 D6 12GB\n" +
        "\n" +
        "270,000원\n" +
        "결제 방법\n" +
        "네이버페이 송금\n" +
        "기타 결제 방식은 판매자와 협의\n" +
        "배송 방법\n" +
        "직거래, 택배 거래\n" +
        "판매자\n" +
        "dk******@naver.com 연락처 보기 \n" +
        "본인인증 완료\n" +
        "\n" +
        "\n" +
        "구매 문의\n" +
        "구매 문의 채팅\n" +
        "\n" +
        "N pay\n" +
        "무료 송금\n" +
        "네이버페이 송금은 에스크로 기능이 제공되지 않으며, 판매자에게 결제금액이 바로 전달되는 ‘일반결제'입니다. \n" +
        "\n" +
        "직접결제 시 아래 사항에 유의해주세요.\n" +
        "카페 구매문의 채팅이나 전화 등을 이용해 연락하고 외부 메신저 이용 및 개인 정보 유출에 주의하세요.\n" +
        "계좌이체 시 선입금을 유도할 경우 안전한 거래인지 다시 한번 확인하세요.\n" +
        "불확실한 판매자(본인 미인증, 해외IP, 사기의심 전화번호)의 물건은 구매하지 말아주세요.\n" +
        "\n" +
        "네이버에 등록된 판매 물품과 내용은 개별 판매자가 등록한 것으로서, 네이버카페는 등록을 위한 시스템만 제공하며 내용에 대하여 일체의 책임을 지지 않습니다.\n" +
        "\n" +
        "\uD83D\uDCA5파격할인! 로봇청소기\uD83E\uDD16 8만원 대 ▶https://tracking.joongna.com/dh1\n" +
        "\uD83D\uDCF2 중고나라 앱 다운받기 ▶https://tracking.joongna.com/a1\n" +
        "거래 전! 카카오톡 간편 사기 조회 ▶https://tracking.joongna.com/kat\n" +
        "───────────────────────\n" +
        "※ 게시글 수집 및 이용 안내 확인 ▶ https://web.joongna.com/post-policy\n" +
        "※ 카페 상품 게시글은 자동으로 중고나라 앱/사이트에 노출합니다. 노출을 원하지 않으실 경우 고객센터로 문의 바랍니다.\n" +
        "※ 중고나라는 통신판매의 당사자가 아니며 상품정보, 거래에 관한 의무와 책임은 각 판매자에게 있습니다.\n" +
        "※ 중고나라 이용정책을 반드시 확인해 주세요. ▶ https://cafe.naver.com/joonggonara/998699127\n" +
        "\n" +
        "... 더보기\n" +
        "※ 카페 상품 게시글은 자동으로 중고나라 앱/사이트에 노출합니다. 노출을 원하지 않으실 경우 고객센터로 문의 바랍니다. \n" +
        "\n" +
        "※ 등록한 게시글이 회원의 신고를 받거나 이상거래로 모니터링 될 경우 중고나라 사기통합조회 DB로 수집/활용될 수 있습니다. \n" +
        "\n" +
        "─────────────────── \n" +
        "\n" +
        "\uD83D\uDCE2 제목에 \"제조사/ 브랜드 명\"과 \"상품명(ex. 맥북 에어 13)”를 넣어 작성하면, 보다 빠른 판매가 가능합니다!  \n" +
        "\n" +
        "\uD83D\uDCE2 게시글 작성 시 배송 방법에 “직거래”와 “내 위치” 설정할 경우, 보다 빠른 판매가 가능합니다! \n" +
        "\n" +
        "\u200B\n" +
        "\n" +
        "박스는 사용할떄 버렸고 2년정도 사용했습니다 뭐 문제없이잘돌아가고 보증기간 살짝남아있어요~\n" +
        "\n" +
        "010-7667-7517 입니다. 네고뭐이런거 안받습니다 귀찮아요\n" +
        "\n" +
        "\n" +
        "\n" +
        "\n" +
        "구매 문의\n" +
        "구매 문의 채팅\n" +
        "\n" +
        "N pay\n" +
        "무료 송금\n" +
        "프로필 사진발냄새노님의 게시글 더보기 \n" +
        " 댓글0\n" +
        " 공유\n" +
        "신고\n" +
        "클린봇이 악성 댓글을 감지합니다.\n" +
        "설정\n" +
        "댓글관심글 댓글 알림\n" +
        "등록\n" +
        "댓글을 입력하세요\n" +
        "블루뽀이\n" +
        "댓글을 남겨보세요\n" +
        "선택된 파일 없음등록\n" +
        " 글쓰기목록 TOP\n" +
        "'rtx 3060' 검색결과\n" +
        "이 카페 글\n" +
        "이 키워드 새글 구독하기\n" +
        "등록\n" +
        "[판매] GTX1660, GTX1080, RTX2060, RTX3060 컴퓨터 팝니다!\t대전아이티월드\t21:54\n" +
        "편집용 게이밍 노트북 HP오멘 rtx3060, i7-12700H, 램 16gb, 512gb ssd\tjyc15\t21:53\n" +
        "[매입][채굴기] RTX 채굴기 체굴기 3080 3070 3060 등 전국출장매입전문업체 퍼스트컴몰입니다.\t퍼스트컴\t21:02\n" +
        "[매입][매입][채굴기]RTX 3060 3070 3080 3090 4090 등 RX,GTX 매입,A4000,A5000,서버\t잇츠 PC매입\t20:56\n" +
        "[매입][채굴기] RTX 채굴기 체굴기 3080 3070 3060 등 전국출장매입전문업체 퍼스트컴몰입니다.\t퍼스트컴\t20:46\n" +
        "페이징 이동12345\n" +
        "전체 카페 글\n" +
        "RTX3060이 RTX4070이 되는 마법!!!댓글[41]\t\t2024.06.09.\n" +
        "RTX 3060 OC댓글[5]\t\t2024.06.07.\n" +
        "MSI 지포스 RTX 3060 벤투스 2X OC D6 8GB 43% 핫딜\t\t2024.06.11.\n" +
        "RTX 3060 TI 이엠텍 지포스 STORM X Dual OC 구매 리뷰 후기 정리\t\t2024.06.09.\n" +
        "RTX 4060 vs RTX 3060 12GB 신형 vs 기존 주류 그래픽카드 비교댓글[3]\t\t2024.05.28.\n" +
        "페이징 이동12345\n" +
        "레이어 닫기\n" +
        "[출처] 갤럭시 GALAX 지포스 RTX 3060 EX WHITE OC V2 D6 12GB (중고나라) | 작성자 발냄새노"

const val item3 =
    " 이전글 다음글목록\n" +
        "[컴퓨터] 노트북 \n" +
        "편집용 게이밍 노트북 HP오멘 rtx3060, i7-12700H, 램 16gb, 512gb ssd\n" +
        "프로필 사진\n" +
        "jyc15\n" +
        "중고나라 회원 \n" +
        "구매문의\n" +
        "2024.06.13. 21:53조회 4\n" +
        "댓글 0URL 복사\n" +
        "상품이미지\n" +
        "디지털/가전 > 노트북\n" +
        "판매(안전) 편집용 게이밍 노트북 HP오멘 rtx3060, i7-12700H, 램 16gb, 512gb ssd\n" +
        "\n" +
        "1,200,000원\n" +
        "상품 상태\n" +
        "사용감 있음\n" +
        "결제 방법\n" +
        "네이버페이 송금, 네이버페이 안전결제 \n" +
        "(무통장, 계좌간편결제 중 선택 가능하며 구매자가\n" +
        "수수료 부담) 기타 결제 방식은 판매자와 협의\n" +
        "\n" +
        "배송 방법\n" +
        "직거래\n" +
        "거래 지역\n" +
        "부평동\n" +
        "판매자\n" +
        "jy******@naver.com  010-89**-77** 연락처 보기 \n" +
        "본인인증 완료\n" +
        "\n" +
        "거래 후기1건0건0건\n" +
        "\n" +
        "구매 문의\n" +
        "구매 문의 채팅\n" +
        "\n" +
        "N pay\n" +
        "안전결제\n" +
        "\n" +
        "N pay\n" +
        "무료 송금\n" +
        "네이버페이 송금은 에스크로 기능이 제공되지 않으며, 판매자에게 결제금액이 바로 전달되는 ‘일반결제'입니다. \n" +
        "\n" +
        "직접결제 시 아래 사항에 유의해주세요.\n" +
        "카페 구매문의 채팅이나 전화 등을 이용해 연락하고 외부 메신저 이용 및 개인 정보 유출에 주의하세요.\n" +
        "계좌이체 시 선입금을 유도할 경우 안전한 거래인지 다시 한번 확인하세요.\n" +
        "불확실한 판매자(본인 미인증, 해외IP, 사기의심 전화번호)의 물건은 구매하지 말아주세요.\n" +
        "\n" +
        "네이버에 등록된 판매 물품과 내용은 개별 판매자가 등록한 것으로서, 네이버카페는 등록을 위한 시스템만 제공하며 내용에 대하여 일체의 책임을 지지 않습니다.\n" +
        "\n" +
        "\uD83D\uDCA5파격할인! 로봇청소기\uD83E\uDD16 8만원 대 ▶https://tracking.joongna.com/dh1\n" +
        "\uD83D\uDCF2 중고나라 앱 다운받기 ▶https://tracking.joongna.com/a1\n" +
        "거래 전! 카카오톡 간편 사기 조회 ▶https://tracking.joongna.com/kat\n" +
        "───────────────────────\n" +
        "※ 게시글 수집 및 이용 안내 확인 ▶ https://web.joongna.com/post-policy\n" +
        "※ 카페 상품 게시글은 자동으로 중고나라 앱/사이트에 노출합니다. 노출을 원하지 않으실 경우 고객센터로 문의 바랍니다.\n" +
        "※ 중고나라는 통신판매의 당사자가 아니며 상품정보, 거래에 관한 의무와 책임은 각 판매자에게 있습니다.\n" +
        "※ 중고나라 이용정책을 반드시 확인해 주세요. ▶ https://cafe.naver.com/joonggonara/998699127\n" +
        "\n" +
        "... 더보기\n" +
        " \n" +
        "\n" +
        " \n" +
        "\n" +
        "노트북 외관 및 사양 인증사진입니다\n" +
        "\n" +
        "HP오멘 게이밍 노트북입니다\n" +
        "\n" +
        "\u200B\n" +
        "\n" +
        "성능은\n" +
        "\n" +
        "\u200B\n" +
        "\n" +
        "그래픽카드 : RTX3060\n" +
        "\n" +
        "CPU : i7-12700H\n" +
        "\n" +
        "램 : 16gb\n" +
        "\n" +
        "용량 : 512gb SSD\n" +
        "\n" +
        "\u200B\n" +
        "\n" +
        "입니다.\n" +
        "\n" +
        "\u200B\n" +
        "\n" +
        "구입당시 170만원에 구입했고 약 1년 정도 사용했습니다.\n" +
        "\n" +
        "관심있으시면 연락주세요~\n" +
        "\n" +
        "태그\n" +
        "#게이밍노트북#게임용노트북#게임용#편집용#편집용노트북#편집노트북#rtx3060#i7#노트북#고사양노트북\n" +
        "\n" +
        "구매 문의\n" +
        "구매 문의 채팅\n" +
        "\n" +
        "N pay\n" +
        "안전결제\n" +
        "\n" +
        "N pay\n" +
        "무료 송금\n" +
        "주의하세요!\n" +
        "\n" +
        "외부 메신저를 통해 결제 링크를 전달하거나 현금 결제를 유도할 경우\n" +
        "위험 거래일 가능성이 높으니 절대 주의하세요.\n" +
        "\n" +
        "프로필 사진jyc15님의 게시글 더보기 \n" +
        " 댓글0\n" +
        " 공유\n" +
        "신고\n" +
        "클린봇이 악성 댓글을 감지합니다.\n" +
        "설정\n" +
        "댓글관심글 댓글 알림\n" +
        "등록\n" +
        "댓글을 입력하세요\n" +
        "블루뽀이\n" +
        "댓글을 남겨보세요\n" +
        "선택된 파일 없음등록\n" +
        " 글쓰기목록 TOP\n" +
        "'rtx 3060' 검색결과\n" +
        "이 카페 글\n" +
        "이 키워드 새글 구독하기\n" +
        "등록\n" +
        "[판매] GTX1660, GTX1080, RTX2060, RTX3060 컴퓨터 팝니다!\t대전아이티월드\t21:54\n" +
        "[매입][채굴기] RTX 채굴기 체굴기 3080 3070 3060 등 전국출장매입전문업체 퍼스트컴몰입니다.\t퍼스트컴\t21:02\n" +
        "[매입][매입][채굴기]RTX 3060 3070 3080 3090 4090 등 RX,GTX 매입,A4000,A5000,서버\t잇츠 PC매입\t20:56\n" +
        "[매입][채굴기] RTX 채굴기 체굴기 3080 3070 3060 등 전국출장매입전문업체 퍼스트컴몰입니다.\t퍼스트컴\t20:46\n" +
        "레노버 리전 5 프로 Legion 5 Pro 16ARH7H 6800H, RTX3060 게이밍노트북\tIQ84\t20:20\n" +
        "페이징 이동12345\n" +
        "전체 카페 글\n" +
        "RTX3060이 RTX4070이 되는 마법!!!댓글[41]\t\t2024.06.09.\n" +
        "RTX 3060 OC댓글[5]\t\t2024.06.07.\n" +
        "MSI 지포스 RTX 3060 벤투스 2X OC D6 8GB 43% 핫딜\t\t2024.06.11.\n" +
        "RTX 3060 TI 이엠텍 지포스 STORM X Dual OC 구매 리뷰 후기 정리\t\t2024.06.09.\n" +
        "RTX 4060 vs RTX 3060 12GB 신형 vs 기존 주류 그래픽카드 비교댓글[3]\t\t2024.05.28.\n" +
        "페이징 이동12345\n" +
        "레이어 닫기\n" +
        "[출처] 편집용 게이밍 노트북 HP오멘 rtx3060, i7-12700H, 램 16gb, 512gb ssd (중고나라) | 작성자 jyc15"
