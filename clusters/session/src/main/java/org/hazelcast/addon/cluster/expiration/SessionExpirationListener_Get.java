package org.hazelcast.addon.cluster.expiration;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryMergedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

/**
 * {@linkplain SessionExpirationListener_Get} traps the 'added', 'updated', and 'merged'
 * events and invokes
 * {@linkplain SessionExpirationService_Get#resetIdleTimeout(String, Object)}
 * to reset the idle timeout on all the session relevant entries from the
 * configured maps.
 * 
 * @author dpark
 *
 */
public class SessionExpirationListener_Get implements EntryAddedListener<Object, Object>, EntryUpdatedListener<Object, Object>,
		EntryMergedListener<Object, Object> {

	@Override
	public void entryUpdated(EntryEvent<Object, Object> event) {
		SessionExpirationService_Get.getExpirationService().resetIdleTimeout(event.getName() /* map name */,
				event.getKey());
	}

	@Override
	public void entryAdded(EntryEvent<Object, Object> event) {
		SessionExpirationService_Get.getExpirationService().resetIdleTimeout(event.getName() /* map name */,
				event.getKey());
	}

	@Override
	public void entryMerged(EntryEvent<Object, Object> event) {
		event.getEventType();
		SessionExpirationService_Get.getExpirationService().resetIdleTimeout(event.getName() /* map name */,
				event.getKey());
	}
}