package ai.gamecopy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamecopy.alipay")
public record AlipayProperties(
    String gatewayUrl,
    String appId,
    String merchantPrivateKey,
    String alipayPublicKey,
    String signType,
    String charset,
    String returnUrl,
    String notifyUrl
) {
}
