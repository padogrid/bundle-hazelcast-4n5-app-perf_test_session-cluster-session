package org.hazelcast.addon.cluster.expiration;

import com.hazelcast.partition.PartitionAware;

/**
 * {@linkplain KeyType} identifies the predicate used to expire (remove) the
 * matching keys in the relevant maps.
 * 
 * @author dpark
 *
 */
public enum KeyType {
	/**
	 * Custom key that works with an implementation of
	 * {@linkplain ISessionIdExtractor} that returns a custom predicate.
	 */
	CUSTOM,

	/**
	 * Key class that implements the {@linkplain ISessionId} interface.
	 */
	INTERFACE,

	/**
	 * Any key object via reflection. Requires a getter method that returns the
	 * session ID.
	 */
	OBJECT,

	/**
	 * Key class that implements the {@linkplain PartitionAware} interface.
	 * {@linkplain PartitionAware#getPartitionKey()#toString()} is the session ID.
	 */
	PARTITION_AWARE,

	/**
	 * String keys. If other key types fail due to improper configuration, etc.,
	 * then the key type defaults to STRING.
	 */
	STRING
}