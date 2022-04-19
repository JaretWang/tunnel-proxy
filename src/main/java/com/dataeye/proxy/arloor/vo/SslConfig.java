package com.dataeye.proxy.arloor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Builder
@Component
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(prefix = "arloor.ssl.config")
public class SslConfig {
    private Integer port;
    private Map<String, String> auth; // base64 - raw
    private String fullchain;
    private String privkey;

//    public SslConfig(Integer port, Map<String, String> auth, String fullchain, String privkey) {
//        this.port = port;
//        this.auth = auth;
//        this.fullchain = fullchain;
//        this.privkey = privkey;
//    }

    public Integer getPort() {
        return port;
    }

    public String getAuth(String base64Auth) {
        return auth.get(base64Auth);
    }

    public Map<String, String> getAuthMap() {
        return auth;
    }

    public String getFullchain() {
        return fullchain;
    }

    public String getPrivkey() {
        return privkey;
    }

    public boolean needAuth() {
        return auth != null && auth.size() != 0;
    }
}
