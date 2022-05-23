package org.hazelcast.addon.cluster.expiration;

public interface SessionExpirationServiceConfiguration {
	
	/**
	 * All expiration property names must begin with this prefix.
	 */
	public final static String PROPERTY_EXPIRATION_PREFIX = "hazelcast.addon.cluster.expiration.";

	/**
	 * Tag used as a prefix to each log message and a part of JMX object name. Default: SessionExpirationService
	 */
	public static final String PROPERTY_TAG = "hazelcast.addon.cluster.expiration.tag";

	/**
	 * Property name prefix for specifying the relevant maps to expire.
	 */
	public final static String PROPERTY_SESSION_PREFIX = "hazelcast.addon.cluster.expiration.session.";
	
	/**
	 * Property for setting the expiration drain size. Each expiration event is
	 * placed in a blocking queue that is drained by a separate consumer thread to
	 * process them. The consumer thread drains the queue based on this value and
	 * processes the expiration events in a batch at a time to provide better
	 * performance. Note that a large drain size will throw stack overflow
	 * exceptions for {@linkplain KeyType#STRING} which ends up building lengthy OR
	 * predicates with LIKE conditions. Hazelcast appears to have undocumented
	 * limitations on lengthy predicates.
	 */
	public final static String PROPERTY_EXPIRATION_QUEUE_DRAIN_SIZE = "hazelcast.addon.cluster.expiration.queue.drain-size";
	
	/**
	 * Property for enabling or disabling session ID postfix for String keys. Default is false.
	 */
	public final static String PROPERTY_STRING_KEY_SESSION_POSTFIX_ENABLED = "hazelcast.addon.cluster.expiration.string-key.postfix.enabled";

	/**
	 * Delimiter separating the session ID from the key value. The last token is the
	 * session ID.
	 */
	public final static String PROPERTY_KEY_DELIMITER = "hazelcast.addon.cluster.expiration.key.delimiter";

	public final static String DEFAULT_KEY_DELIMTER = "@";

	public final static String JMX_USE_HAZELCAST_OBJECT_NAME = "hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name";

	public final static String NAME_TAG = "%TAG%";

	/**
	 * Default expiration queue drain size.
	 */
	public final static int DEFAULT_EXPIRATION_QUEUE_DRAIN_SIZE = 100;
}
