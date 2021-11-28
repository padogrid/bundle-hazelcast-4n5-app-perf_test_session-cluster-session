package org.hazelcast.addon.cluster.expiration;

public final class SessionExpirationServiceConfiguration {
	
	public static final String PROPERTY_TAG = "hazelcast.addon.cluster.expiration.tag";

	/**
	 * Property name prefix for specifying the relevant maps to expire.
	 */
	public final static String PROPERTY_SESSION_PREFIX = "hazelcast.addon.cluster.expiration.session.";

	/**
	 * Delimiter separating the session ID (UUID) and the remainder.
	 */
	public final static String PROPERTY_KEY_DELIMITER = "hazelcast.addon.cluster.expiration.key.delimiter";

	public final static String DEFAULT_KEY_DELIMTER = "_";
	
	public final static String JMX_USE_HAZELCAST_OBJECT_NAME = "hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name";
}