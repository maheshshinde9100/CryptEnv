package com.maheshshinde.CryptEnv.provider;

/**
 * A push target within a provider — a repository, project, worker, namespace, etc.
 *
 * @param id   provider-specific identifier used as the sync target
 * @param name human readable label
 * @param url  optional deep link to the target in the provider console
 */
public record ProviderTarget(String id, String name, String url) {
}
