package org.hazelcast.addon.cluster.expiration;

/**
 * Key classes implementing this interface can provide the session ID required
 * by {@linkplain SessionExpirationService} for the primary session maps
 * configured with the key type {@linkplain KeyType#INTERFACE}.
 * 
 * @author dpark
 *
 */
public interface ISessionId {
	public String getSessionId();
}
