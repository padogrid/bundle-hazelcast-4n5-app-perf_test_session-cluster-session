package org.hazelcast.addon.cluster.expiration.metadata;

import org.hazelcast.addon.cluster.expiration.SessionExpirationService_Get;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryMergedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

/**
 * {@linkplain SessionExpirationListener_SessionMetadata_Get} traps the 'added',
 * 'updated', and 'merged' events and invokes
 * {@linkplain SessionExpirationService_Get#resetIdleTimeout(String, Object)}
 * to reset the idle timeout on all the session relevant entries from the
 * configured maps.
 * 
 * @author dpark
 *
 */
public class SessionExpirationListener_SessionMetadata_Get implements EntryAddedListener<Object, SessionMetadata>,
		EntryUpdatedListener<Object, SessionMetadata>, EntryMergedListener<Object, SessionMetadata> {

	@Override
	public void entryUpdated(EntryEvent<Object, SessionMetadata> event) {
		SessionExpirationService_SessionMetadata_Get.getExpirationService()
				.reset(event.getValue());
	}

	@Override
	public void entryAdded(EntryEvent<Object, SessionMetadata> event) {
		entryUpdated(event);
	}

	@Override
	public void entryMerged(EntryEvent<Object, SessionMetadata> event) {
		entryUpdated(event);
	}
}