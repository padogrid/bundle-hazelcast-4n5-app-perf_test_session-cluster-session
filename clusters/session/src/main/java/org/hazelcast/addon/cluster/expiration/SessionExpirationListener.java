package org.hazelcast.addon.cluster.expiration;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryExpiredListener;

/**
 * {@linkplain SessionExpirationListener} traps the expiration events and
 * invokes {@linkplain SessionExpirationService#expire(String, String)} to
 * expire all the session relevant entries from the configured maps.
 * 
 * @author dpark
 *
 */
public class SessionExpirationListener implements EntryExpiredListener<String, Object> {

	@Override
	public void entryExpired(EntryEvent<String, Object> event) {
		SessionExpirationService.getExpirationService().expire(event.getName(), event.getKey());
	}

}
