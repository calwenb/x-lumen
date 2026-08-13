package com.calwen.xlumen.identity.service.impl;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.identity.service.RefreshTokenService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 刷新令牌实现（F-0101）：Redis key 为哈希值，value 为 "userId:workspaceId"；
 * 轮换使用 GETDEL 原子操作，旧令牌一次性失效（BACKEND.md §15.3 防重放）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String KEY_PREFIX = "xlumen:refresh:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String create(Long userId, Long workspaceId) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stringRedisTemplate.opsForValue().set(
                keyOf(token),
                userId + ":" + workspaceId,
                Duration.ofSeconds(REFRESH_TOKEN_TTL_SECONDS));
        return token;
    }

    @Override
    public RefreshSession rotate(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            return null;
        }
        // GETDEL 原子取旧值：成功即旧令牌失效，防重放
        String value = stringRedisTemplate.opsForValue().getAndDelete(keyOf(refreshToken));
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return new RefreshSession(Long.valueOf(parts[0]), Long.valueOf(parts[1]));
    }

    @Override
    public void revoke(String refreshToken) {
        if (StrUtil.isNotBlank(refreshToken)) {
            stringRedisTemplate.delete(keyOf(refreshToken));
        }
    }

    /** 令牌 SHA-256 哈希作为 Redis key（不存明文）。 */
    private String keyOf(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return KEY_PREFIX + HexUtil.encodeHexStr(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
