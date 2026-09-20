package com.devlensai.backend.ai;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Administrator-supplied endpoints only; request data contains profile IDs, never URLs. */
public class OllamaConnections {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    private final Map<String, Profile> profiles;

    public OllamaConnections(String configured) {
        Map<String, Profile> parsed = new LinkedHashMap<>();
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("OLLAMA_PROFILES must contain at least one trusted endpoint");
        }
        for (String entry : configured.split(",", -1)) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length != 3 || !ID.matcher(parts[0]).matches() || parts[1].isBlank()
                    || parts[1].length() > 80 || parsed.containsKey(parts[0])) {
                throw new IllegalArgumentException("OLLAMA_PROFILES contains an invalid profile");
            }
            URI base = trustedBaseUrl(parts[2]);
            parsed.put(parts[0], new Profile(parts[0], parts[1], base));
        }
        if (parsed.size() > 10) {
            throw new IllegalArgumentException("OLLAMA_PROFILES exceeds ten profiles");
        }
        profiles = Collections.unmodifiableMap(parsed);
    }

    public List<Profile> list() {
        return List.copyOf(profiles.values());
    }

    public Profile require(String id) {
        Profile profile = profiles.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown Ollama connection profile");
        }
        return profile;
    }

    private static URI trustedBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"http".equals(uri.getScheme()) || host == null || uri.getPort() < 1
                    || uri.getPort() > 65535 || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                    && !"/".equals(uri.getRawPath()))) {
                throw new IllegalArgumentException();
            }
            if (!"localhost".equalsIgnoreCase(host) && !"host.docker.internal".equalsIgnoreCase(host)
                    && !"127.0.0.1".equals(host) && !"::1".equals(host) && !privateIpv4(host)) {
                throw new IllegalArgumentException();
            }
            return URI.create("http://" + (host.contains(":") ? "[" + host + "]" : host)
                    + ":" + uri.getPort() + "/");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("OLLAMA_PROFILES requires loopback or private-LAN HTTP endpoints");
        }
    }

    private static boolean privateIpv4(String host) {
        if (!IPV4.matcher(host).matches()) return false;
        String[] parts = host.split("\\.");
        if (Arrays.stream(parts).anyMatch(part -> part.length() > 1 && part.startsWith("0"))) return false;
        int[] octets = Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
        if (Arrays.stream(octets).anyMatch(octet -> octet > 255)) return false;
        return octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    public record Profile(String id, String displayName, URI baseUrl) {
    }
}
