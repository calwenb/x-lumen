package com.calwen.xlumen.ai.service.provider;

/**
 * 流式回调（IDEA-025 F-0708）：onContent 内容增量逐块回调；
 * onResult 流结束（[DONE] 或流尾）时回调一次聚合终态（toolCalls/finishReason）。
 *
 * @author calwen
 * @date 2026/8/24
 */
public interface StreamCallback {

    /** 内容增量。 */
    void onContent(String delta);

    /** 流终态（聚合后的 ProviderChatResult）。 */
    void onResult(ProviderChatResult result);
}