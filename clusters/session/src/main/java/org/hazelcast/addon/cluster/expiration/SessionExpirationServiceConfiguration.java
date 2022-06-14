package org.hazelcast.addon.cluster.expiration;

public interface SessionExpirationServiceConfiguration {
	
	/**
	 * All expiration property names must begin with this prefix.
	 */
	public final static String PROPERTY_EXPIRATION_PREFIX = "hazelcast.addon.cluster.expiration.";

	/**
	 * Tag used as a prefix to each log message and a part of JMX object name. Default: SessionExpirationService
	 */
	public static final String PROPERTY_TAG = PROPERTY_EXPIRATION_PREFIX + "tag";

	/**
	 * Property name prefix for specifying the relevant maps to expire.
	 */
	public final static String PROPERTY_SESSION_PREFIX = PROPERTY_EXPIRATION_PREFIX + "session.";
	
	/**
	 * Property for setting the expiration drain size. Each expiration event is
	 * placed in a blocking queue that is drained by a separate worker thread to
	 * process them. The worker thread drains the queue based on this value and
	 * processes the expiration events in a batch at a time to provide better
	 * performance. Note that a large drain size will throw stack overflow
	 * exceptions for {@linkplain KeyType#STRING} which ends up building lengthy OR
	 * predicates with LIKE conditions. Hazelcast appears to have undocumented
	 * limitations on lengthy predicates.
	 */
	public final static String PROPERTY_EXPIRATION_QUEUE_DRAIN_SIZE = PROPERTY_EXPIRATION_PREFIX  + "queue.drain-size";
	
	/**
	 * Property for enabling or disabling session ID postfix for String keys. Default is false.
	 */
	public final static String PROPERTY_STRING_KEY_SESSION_POSTFIX_ENABLED = PROPERTY_EXPIRATION_PREFIX +  "string-key.postfix.enabled";

	/**
	 * Delimiter separating the session ID from the key value. The last token is the
	 * session ID.
	 */
	public final static String PROPERTY_KEY_DELIMITER = PROPERTY_EXPIRATION_PREFIX +  "key.delimiter";
	
	/**
	 * Worker thread pool size.
	 */
	public final static String PROPERTY_EXPIRATION_THREAD_POOL_SIZE = PROPERTY_EXPIRATION_PREFIX +  "thread.pool-size";

	public final static String DEFAULT_KEY_DELIMTER = "@";

	public final static String JMX_USE_HAZELCAST_OBJECT_NAME =  PROPERTY_EXPIRATION_PREFIX +  "jmx-use-hazelcast-object-name";

	public final static String NAME_TAG = "%TAG%";

	/**
	 * Default session expiration worker thread pool size.
	 */
	public final static int DEFAULT_EXPIRATION_THREAD_POOL_SIZE = 1;
	
	/**
	 * Default expiration queue drain size.
	 */
	public final static int DEFAULT_EXPIRATION_QUEUE_DRAIN_SIZE = 100;
}
