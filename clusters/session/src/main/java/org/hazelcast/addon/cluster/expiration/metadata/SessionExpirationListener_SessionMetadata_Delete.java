package org.hazelcast.addon.cluster.expiration.metadata;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryExpiredListener;

/**
 * {@linkplain SessionExpirationListener_SessionMetadata_Delete} traps the expiration
 * events and invokes
 * {@linkplain SessionExpirationService#expire(String, Object)} to expire all
 * the session relevant entries from the configured maps.
 * 
 * @author dpark
 *
 */
public class SessionExpirationListener_SessionMetadata_Delete implements EntryExpiredListener<String, SessionMetadata> {

	@Override
	public void entryExpired(EntryEvent<String, SessionMetadata> event) {
		SessionMetadata sm = event.getOldValue();
		SessionExpirationService_SessionMetadata_Delete.getExpirationService().expire(event.getName(), sm);
	}
}