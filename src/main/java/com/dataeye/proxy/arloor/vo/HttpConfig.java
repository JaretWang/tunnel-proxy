package com.dataeye.proxy.arloor.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@Component
@AllArgsConstructor
@NoArgsConstructor
//@ConfigurationProperties(prefix = "arloor.http.config")
public class HttpConfig {
    private Integer port;
    private Map<String, String> auth; // base64 - raw
    private Set<String> domainWhiteList = new HashSet<>();

//    public HttpConfig(Integer port, Map<String, String> auth, Set<String> domainWhiteList) {
//        this.port = port;
//        this.auth = auth;
//        if (domainWhiteList != null) {
//            this.domainWhiteList = domainWhiteList;
//        }
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

    public boolean needAuth() {
        return auth != null && auth.size() != 0;
    }

    public Set<String> getDomainWhiteList() {
        return domainWhiteList;
    }
}
