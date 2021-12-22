package org.hazelcast.addon.cluster.expiration;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryExpiredListener;

/**
 * {@linkplain SessionExpirationListener} traps the expiration events and
 * invokes {@linkplain SessionExpirationService#expire(String, Object)} to
 * expire all the session relevant entries from the configured maps.
 * 
 * @author dpark
 *
 */
public class SessionExpirationListener implements EntryExpiredListener<Object, Object> {

	@Override
	public void entryExpired(EntryEvent<Object, Object> event) {
		SessionExpirationService.getExpirationService().expire(event.getName() /* map name */, event.getKey());
	}
}