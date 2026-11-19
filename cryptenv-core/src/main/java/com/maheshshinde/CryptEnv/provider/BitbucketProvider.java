package com.maheshshinde.CryptEnv.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.maheshshinde.CryptEnv.provider.ProviderHttp.enc;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.get;
import static com.maheshshinde.CryptEnv.provider.ProviderHttp.send;

/** Bitbucket Cloud repository Pipeline variables. Target format is workspace/repository. */
@Component
public class BitbucketProvider implements SecretProvider {
    private static final String API = "https://api.bitbucket.org/2.0";

    @Override public String providerId() { return "bitbucket"; }
    @Override public String displayName() { return "Bitbucket"; }

    @Override
    public String checkHealth(ProviderCredentials credentials) {
        JsonNode user = get(API + "/user", headers(credentials));
        return user.path("display_name").asText(user.path("username").asText("connected"));
    }

    @Override
    public List<ProviderTarget> listTargets(ProviderCredentials credentials) {
        JsonNode result = get(API + "/repositories?role=member&pagelen=100", headers(credentials));
        List<ProviderTarget> targets = new ArrayList<>();
        for (JsonNode repository : result.path("values")) {
            String fullName = repository.path("full_name").asText(null);
            if (fullName != null) targets.add(new ProviderTarget(fullName, fullName,
                    repository.path("links").path("html").path("href").asText(null)));
        }
        return targets;
    }

    @Override
    public void pushSecret(ProviderCredentials credentials, String target, String key, String value) {
        String base = variablesUrl(target);
        JsonNode variable = findVariable(credentials, base, key);
        Map<String, Object> body = Map.of("key", key, "value", value, "secured", true);
        if (variable == null) {
            send(HttpMethod.POST, base, headers(credentials), body);
        } else {
            String id = variable.path("uuid").asText();
            send(HttpMethod.PUT, base + "/" + enc(id), headers(credentials), body);
        }
    }

    @Override
    public void deleteSecret(ProviderCredentials credentials, String target, String key) {
        String base = variablesUrl(target);
        JsonNode variable = findVariable(credentials, base, key);
        if (variable != null) send(HttpMethod.DELETE, base + "/" + enc(variable.path("uuid").asText()), headers(credentials), null);
    }

    private JsonNode findVariable(ProviderCredentials credentials, String url, String key) {
        for (JsonNode variable : get(url, headers(credentials)).path("values")) {
            if (key.equals(variable.path("key").asText())) return variable;
        }
        return null;
    }

    private String variablesUrl(String target) {
        String[] parts = target == null ? new String[0] : target.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
            throw new ProviderException("Bitbucket target must be in workspace/repository form");
        return API + "/repositories/" + enc(parts[0]) + "/" + enc(parts[1]) + "/pipelines_config/variables";
    }

    private Map<String, String> headers(ProviderCredentials credentials) {
        return Map.of("Authorization", "Bearer " + credentials.token());
    }
}
