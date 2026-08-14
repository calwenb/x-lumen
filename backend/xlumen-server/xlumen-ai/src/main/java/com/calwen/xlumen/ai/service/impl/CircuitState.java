package com.calwen.xlumen.ai.service.impl;

/**
 * 熔断状态（F-0501）：连续失败计数与打开截止时间。
 * 仅被 {@link ModelGatewayImpl} 经 ConcurrentHashMap 访问，线程可见性由 CHM 保证。
 *
 * @author calwen
 * @date 2026/8/14
 */
final class CircuitState {
    int failures;
    long openUntil;
}
