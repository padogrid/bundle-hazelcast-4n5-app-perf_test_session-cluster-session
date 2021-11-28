package org.hazelcast.addon.cluster.expiration;

/**
 * {@linkplain SessionExpirationService} status.
 * @author dpark
 *
 */
public interface SessionExpirationServiceStatusMBean {
	/**
	 * @return Expiration event queue size.
	 */
	Integer getQueueSize();
}
