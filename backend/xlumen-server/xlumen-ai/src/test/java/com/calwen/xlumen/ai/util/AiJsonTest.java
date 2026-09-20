package com.calwen.xlumen.ai.util;

import cn.hutool.json.JSONArray;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 输出 JSON 提取工具单测：严格解析语义不变，宽容解析可抢救被截断数组中的完整元素。
 *
 * @author calwen
 * @date 2026/9/14
 */
class AiJsonTest {

    private static final String ONE = "{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"E1\",\"suggestion\":\"S1\"}";
    private static final String TWO = "{\"severity\":\"warning\",\"position\":\"L2\",\"evidence\":\"E2\",\"suggestion\":\"S2\"}";

    @Test
    void strictParse_stillRejectsTruncatedArray() {
        // 既有 extractArray 语义保持不变：截断数组返回 null
        assertThat(AiJson.extractArray("[" + ONE + "," + TWO)).isNull();
    }

    @Test
    void lenient_salvagesCompleteObjectsFromTruncatedArray() {
        JSONArray arr = AiJson.extractArrayLenient("[" + ONE + "," + TWO);

        assertThat(arr).isNotNull();
        assertThat(arr).hasSize(2);
        assertThat(arr.getJSONObject(0).getStr("position")).isEqualTo("L1");
    }

    @Test
    void lenient_salvagesOnlyBalancedPrefix() {
        // 第三个元素被截断 → 只抢救出前两个完整对象
        JSONArray arr = AiJson.extractArrayLenient("[" + ONE + "," + TWO
                + ",{\"severity\":\"info\",\"position\":\"L3\",\"evidence\":\"E3");

        assertThat(arr).isNotNull();
        assertThat(arr).hasSize(2);
    }

    @Test
    void lenient_stripsFenceAndPlainText() {
        JSONArray arr = AiJson.extractArrayLenient("```json\n[" + ONE + "]\n```");

        assertThat(arr).isNotNull().hasSize(1);
    }

    @Test
    void lenient_returnsNullWhenNoCompleteElement() {
        assertThat(AiJson.extractArrayLenient("不是 JSON")).isNull();
        assertThat(AiJson.extractArrayLenient("[{\"severity\":\"error\"")).isNull();
    }

    @Test
    void lenient_bracesInsideStringDoNotBreakScan() {
        String item = "{\"severity\":\"error\",\"position\":\"L1\",\"evidence\":\"含 } 与 [ 的文本\",\"suggestion\":\"S1\"}";
        JSONArray arr = AiJson.extractArrayLenient("[" + item + "," + TWO.substring(0, 20));

        assertThat(arr).isNotNull().hasSize(1);
        assertThat(arr.getJSONObject(0).getStr("evidence")).contains("}");
    }
}
