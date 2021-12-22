package org.hazelcast.addon.cluster.expiration;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.hazelcast.addon.cluster.ClusterUtil;
import org.hazelcast.addon.cluster.MapUtil;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.map.IMap;
import com.hazelcast.partition.PartitionAware;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;

/**
 * {@linkplain SessionExpirationService} is a singleton class that expires all
 * the specified session relevant entries from the pattern matching maps.
 * 
 * @author dpark
 *
 */
public class SessionExpirationService {

	private final static SessionExpirationService expirationService = new SessionExpirationService();

	private ILogger logger = null;

	private Consumer consumer;
	private Thread consumerThread;
	private BlockingQueue<SessionInfo> queue = new LinkedBlockingQueue<SessionInfo>();
	private HazelcastInstance hazelcastInstance;

	private String tag;
	private String logPrefix;
	private boolean isJmxEnabled;
	private SessionExpirationServiceStatus status;
	private boolean isJmxUseHazelcastObjectName;

	// delimiter is used for STRING key type only
	private String delimiter = SessionExpirationServiceConfiguration.DEFAULT_KEY_DELIMTER;

	// tagMap contains <tagged primary map name, SessionData> entries
	private HashMap<String, SessionData> tagMap = new HashMap<String, SessionData>(10);

	// sessionMaps contains <actual primary map name, SessionTag> entries
	private Map<String, SessionTag> sessionMap = new HashMap<String, SessionTag>(10);

	public final static SessionExpirationService getExpirationService() {
		return expirationService;
	}

	private SessionExpirationService() {
	}

	/**
	 * Initializes the session service.
	 * 
	 * @param properties
	 */
	@SuppressWarnings("rawtypes")
	synchronized void initialize(Properties properties) {

		// Get the first HazelcastInstance
		Set<HazelcastInstance> set = Hazelcast.getAllHazelcastInstances();
		for (HazelcastInstance hz : set) {
			hazelcastInstance = hz;
			logger = hazelcastInstance.getLoggingService().getLogger(this.getClass());
			break;
		}

		// tag used for logging and JMX only. This tag is different from
		// SessionExpirationServiceConfiguration.NAME_TAG which applies to SessionTag.
		tag = properties.getProperty(SessionExpirationServiceConfiguration.PROPERTY_TAG,
				SessionExpirationService.class.getSimpleName());
		logPrefix = tag + ": ";
		int index = SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX.length();
		String[] split = SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX.split("\\.");
		int primaryMapNamePrefixTokenCount = split.length + 1;
		StringBuffer buffer = new StringBuffer();
		for (Map.Entry entry : properties.entrySet()) {
			String key = (String) entry.getKey();
			if (key.startsWith(SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX)) {
				split = key.split("\\.");
				if (split.length > primaryMapNamePrefixTokenCount) {
					continue;
				}
				String taggedPrimaryMapName = key.substring(index);
				String mapNames = (String) entry.getValue();
				String[] names = mapNames.split(",");
				SessionData sessionData = tagMap.get(taggedPrimaryMapName);
				if (sessionData == null) {
					sessionData = new SessionData(taggedPrimaryMapName);
					tagMap.put(taggedPrimaryMapName, sessionData);
				}
				sessionData.relevantMapNames = names;

				if (buffer.length() > 0) {
					buffer.append(", ");
				}
				buffer.append(taggedPrimaryMapName + ": " + mapNames);

				// key.property
				sessionData.keyProperty = properties
						.getProperty(SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX
								+ taggedPrimaryMapName + ".key.property");
				if (sessionData.keyProperty != null && sessionData.keyProperty.length() > 0) {
					// Change the first letter to upper case for constructing the get method.
					sessionData.getterMethodName = "get" + sessionData.keyProperty.substring(0, 1).toUpperCase()
							+ sessionData.keyProperty.substring(1);
				}

				// key.type
				String keyTypeStr = properties.getProperty(SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX
						+ taggedPrimaryMapName + ".key.type");
				if (keyTypeStr != null) {
					try {
						// keyType is case-insensitive (change to upper case)
						sessionData.keyType = KeyType.valueOf(keyTypeStr.toUpperCase());
					} catch (Exception ex) {
						if (logger != null) {
							logger.warning(logPrefix + taggedPrimaryMapName + ".key.type - Invalid key type ["
									+ keyTypeStr + "]. Defaulting to " + KeyType.STRING);
						}
						sessionData.keyType = KeyType.STRING;
					}
				}
				if (sessionData.keyType == KeyType.CUSTOM) {
					String sessionIdPredicateClassName = properties
							.getProperty(SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX
									+ taggedPrimaryMapName + ".key.predicate");
					if (sessionIdPredicateClassName == null) {
						logger.warning(logPrefix + taggedPrimaryMapName + ".key.predicate undefined for the key type "
								+ KeyType.CUSTOM + ". Defaulting to " + KeyType.STRING);
						sessionData.keyType = KeyType.STRING;
					} else {
						try {
							Class clazz = Class.forName(sessionIdPredicateClassName);
							Object obj = clazz.newInstance();
							if (obj instanceof ISessionIdPredicate) {
								sessionData.sessionIdPredicate = (ISessionIdPredicate) obj;
							} else {
								if (logger != null) {
									logger.warning(logPrefix + taggedPrimaryMapName + ".key.predicate"
											+ " - Invalid session Id predicate class [ " + sessionIdPredicateClassName
											+ "]. It must implement " + ISessionIdPredicate.class.getCanonicalName()
											+ ". Defaulting to " + KeyType.STRING);
								}
								sessionData.keyType = KeyType.STRING;
							}
						} catch (ClassNotFoundException e) {
							if (logger != null) {
								logger.warning(logPrefix + taggedPrimaryMapName + ".key.predicate"
										+ " -  Session ID predicate class not found [" + sessionIdPredicateClassName
										+ "]. Defaulting to " + KeyType.STRING);
							}
							sessionData.keyType = KeyType.STRING;
						} catch (InstantiationException | IllegalAccessException e) {
							if (logger != null) {
								logger.warning(logPrefix + taggedPrimaryMapName + ".key.predicate"
										+ " - Exception occurred while creating session ID predicate class ["
										+ sessionIdPredicateClassName + "]. Defaulting to " + KeyType.STRING);
							}
							sessionData.keyType = KeyType.STRING;
						}
					}
				}
			}
		}
		delimiter = properties.getProperty(SessionExpirationServiceConfiguration.PROPERTY_KEY_DELIMITER,
				SessionExpirationServiceConfiguration.DEFAULT_KEY_DELIMTER);

		String bool = properties.getProperty(SessionExpirationServiceConfiguration.JMX_USE_HAZELCAST_OBJECT_NAME,
				"false");
		isJmxUseHazelcastObjectName = bool.equalsIgnoreCase("true");

		// Start thread as daemon
		consumer = new Consumer(queue);
		consumerThread = new Thread(consumer, SessionExpirationService.class.getSimpleName());
		consumerThread.setDaemon(true);
		consumerThread.start();

		if (logger != null) {
			logger.info(logPrefix + this.getClass().getCanonicalName() + " started: delimiter=\"" + delimiter + "\" ["
					+ buffer.toString() + "]");
		}

		if (hazelcastInstance != null) {
			String jmxEnabled = hazelcastInstance.getConfig().getProperty("hazelcast.jmx");
			isJmxEnabled = jmxEnabled != null && jmxEnabled.equalsIgnoreCase("true");
		} else {
			isJmxEnabled = Boolean.getBoolean("hazelcast.jmx");
		}
		if (isJmxEnabled) {
			// MBean
			status = new SessionExpirationServiceStatus();
			updateMBean();

			// Register the object in the MBeanServer
			MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
			ObjectName objectName;
			String instanceName = null;
			if (hazelcastInstance == null) {
				instanceName = "hazelcast";
			} else {
				instanceName = hazelcastInstance.getName();
			}
			try {
				String header;
				String type;
				if (isJmxUseHazelcastObjectName) {
					header = "com.hazelcast";
					type = "Metrics";
				} else {
					header = "org.hazelcast.addon";
					type = SessionExpirationService.class.getSimpleName();
				}
				objectName = new ObjectName(header + ":name=" + SessionExpirationService.class.getSimpleName()
						+ ",instance=" + instanceName + ",type=" + type + ",tag=" + tag);
				platformMBeanServer.registerMBean(status, objectName);
				logger.info(logPrefix + SessionExpirationServiceStatusMBean.class.getSimpleName()
						+ " registered: objectName=" + objectName.toString() + ", tag=" + tag);
			} catch (MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException
					| NotCompliantMBeanException ex) {
				logger.warning(logPrefix + SessionExpirationServiceStatusMBean.class.getSimpleName()
						+ " registration error: tag=" + tag, ex);
			}
		}
	}

	/**
	 * Adds a {@linkplain SessionTag} entry into the session map if the specified
	 * session map name has a matching tag configured.
	 * 
	 * @param sessionMapName Session map name.
	 * @return null if a matching tag is not configured.s
	 */
	private SessionTag addSessionTag(String sessionMapName) {
		Set<Map.Entry<String, SessionData>> set = tagMap.entrySet();
		SessionTag sessionTag = null;
		for (Map.Entry<String, SessionData> entry : set) {
			String tag = SessionMapUtil.getTag(sessionMapName, entry.getKey());
			if (tag == null) {
				// tag mismatch
				continue;
			} else {
				// if tag.length() == 0 then tag not specified, i.e., exact match, otherwise tag
				// specified.
				SessionData sessionData = entry.getValue();
				sessionTag = new SessionTag(sessionData);
				sessionTag.tag = tag;
				sessionTag.sessionMapName = sessionMapName;
				sessionMap.put(sessionMapName, sessionTag);
				break;
			}
		}
		return sessionTag;
	}

	/**
	 * Returns the session tag pertaining to the specified session map name.
	 * 
	 * @param sessionMapName Session map name
	 * @return null if the specified session map name does not have session tag.
	 */
	private SessionTag getSessionTag(String sessionMapName) {
		SessionTag sessionTag = sessionMap.get(sessionMapName);
		if (sessionTag == null) {
			sessionTag = addSessionTag(sessionMapName);
		}
		return sessionTag;
	}

	/**
	 * Expires all the entries from the configured maps that have the matching
	 * session ID extracted from the specified key.
	 * 
	 * @param sessionMapName The expiration originated map.
	 * @param key            Key object containing the session ID.
	 */
	public void expire(String sessionMapName, Object key) {
		if (consumerThread.isAlive() == false) {
			return;
		}
		if (key == null) {
			return;
		}
		SessionTag sessionTag = getSessionTag(sessionMapName);
		if (sessionTag == null) {
			return;
		}
		queue.offer(new SessionInfo(sessionMapName, key));
		updateMBean();
	}

	/**
	 * Terminates the service thread. Note that it will not terminate if the
	 * underlying expiration event queue is empty. It will block until queue has at
	 * least one session ID to be processed.
	 */
	public void terminate() {
		if (consumer != null) {
			consumer.terminate();
		}
	}

	/**
	 * Returns the underlying expiration event queue size.
	 * 
	 * @return
	 */
	public int getQueueSize() {
		return queue.size();
	}

	/**
	 * Returns the session key string delimiter. The default delimiter is "@".
	 */
	public String getDelimiter() {
		return delimiter;
	}

	/**
	 * Returns true if the {@linkplain #terminate()} method is invoked but it is
	 * still in termination state. The thread maybe in the terminating state until
	 * the next expiration event occurs if the thread is currently blocked on the
	 * empty queue.
	 */
	public boolean isTerminating() {
		return consumer.isTerminating();
	}

	/**
	 * Updates MBean if enabled.
	 */
	private void updateMBean() {
		if (isJmxEnabled) {
			// Update MBean
			status.setQueueSize(getQueueSize());
		}
	}

	/**
	 * {@linkplain Consumer} takes {@linkplain SessionInfo} objects from the
	 * blocking queue and expires session entries from the relevant maps.
	 * 
	 * @author dpark
	 *
	 */
	class Consumer implements Runnable {

		protected BlockingQueue<SessionInfo> queue = null;
		private boolean shouldRun = true;
		private boolean isTerminated = false;

		public Consumer(BlockingQueue<SessionInfo> queue) {
			this.queue = queue;
		}

		@SuppressWarnings("rawtypes")
		public void run() {
			while (shouldRun) {
				SessionInfo sessionInfo = new SessionInfo("", null);
				try {
					sessionInfo = queue.take();
					updateMBean();
					SessionTag sessionTag = sessionMap.get(sessionInfo.sessionMapName);
					if (sessionTag == null) {
						continue;
					}
					SessionData sessionData = sessionTag.sessionData;
					if (sessionData == null || sessionData.relevantMapNames == null || sessionTag.tag == null) {
						continue;
					}

					// Expire or remove all session specific entries from
					// all the maps.
					Predicate predicate = null;

					switch (sessionData.keyType) {
					case INTERFACE:
						if (sessionInfo.key instanceof ISessionId) {
							String sessionId = ((ISessionId) sessionInfo.key).getSessionId();
							predicate = Predicates.equal("__key.sessionId", sessionId);
						}
						break;

					case OBJECT:
						if (sessionData.keyProperty == null) {
							break;
						}
						try {
							Method method = sessionInfo.key.getClass().getMethod(sessionData.getterMethodName);
							Object sessionId = method.invoke(sessionInfo.key);
							if (sessionId != null) {
								predicate = Predicates.equal("__key." + sessionData.keyProperty, sessionId.toString());
							}
						} catch (Exception ex) {
							logger.warning(logPrefix + "Exception ocurred while processing " + KeyType.OBJECT
									+ " key type for expiring the relevant maps for " + sessionInfo.sessionMapName
									+ ".", ex);
						}
						break;

					case PARTITION_AWARE:
						if (sessionInfo.key instanceof PartitionAware) {
							Object partitionKey = ((PartitionAware) sessionInfo.key).getPartitionKey();
							if (partitionKey != null) {
								predicate = Predicates.equal("__key.partitionKey", partitionKey.toString());
							}
						}
						break;

					case CUSTOM:
						if (sessionData.sessionIdPredicate != null) {
							predicate = sessionData.sessionIdPredicate.getPredicate(sessionInfo.sessionMapName,
									sessionInfo.key);
						}
						break;

					case STRING:
					default:
						/*
						 * The session ID is the last part of the key string separated by the delimiter.
						 * If the key does not contain the delimiter, then the entire key string is used
						 * as the session ID.
						 */
						String keyStr = sessionInfo.key.toString();
						int index = keyStr.lastIndexOf(delimiter);
						String sessionId;
						if (index == -1) {
							sessionId = keyStr;
						} else {
							sessionId = keyStr.substring(index + delimiter.length());
						}
						if (sessionId.length() != 0) {
							predicate = Predicates.like("__key", "%" + sessionId);
						}
						break;
					}

					// If predicate is null then unable to expire entries in the relevant maps.
					if (predicate == null) {
						continue;
					}

					Map<String, IMap> maps = ClusterUtil.getAllMaps(hazelcastInstance);
					for (String relevantMapName : sessionData.relevantMapNames) {
						if (sessionTag.tag.length() > 0) {
							relevantMapName = relevantMapName.replaceAll(SessionExpirationServiceConfiguration.NAME_TAG,
									sessionTag.tag);
						}
						for (Map.Entry<String, IMap> entry : maps.entrySet()) {
							if (entry.getKey().matches(relevantMapName)) {
								IMap map = entry.getValue();
								MapUtil.removeMemberAllKeySet(map, predicate);
							}
						}
					}
					if (logger != null && logger.isFineEnabled()) {
						logger.fine(logPrefix + "Expired session: " + sessionInfo);
					}

				} catch (InterruptedException ex) {
					logger.info(logPrefix + "InterruptedException exception ignored.", ex);
				} catch (Throwable ex) {
					logger.warning(logPrefix + "Exception occurred while applying predicate to expire relevant maps ["
							+ sessionInfo.sessionMapName + "]", ex);
				}
			}
			isTerminated = true;
			queue.clear();
			updateMBean();
		}

		/**
		 * Returns true if the {@linkplain #terminate()} method is invoked but it is
		 * still in termination state. It will terminate only when the current session
		 * ID (or the next session ID if the queue is empty) has been processed.
		 */
		public boolean isTerminating() {
			return !shouldRun && !isTerminated;
		}

		/**
		 * Terminates the consumer thread. Upon termination, the blocking queue will be
		 * cleared and the consumer thread is no longer usable. Note that it will not
		 * terminate if the queue is empty. It will block until queue has at least one
		 * session ID to be processed.
		 */
		public void terminate() {
			shouldRun = false;
		}

		/**
		 * Returns true if the consumer thread has been terminated.
		 * 
		 * @return
		 */
		public boolean isTerminated() {
			return isTerminated;
		}
	}

	/**
	 * {@linkplain SessionInfo} holds session map name and key.
	 * {@linkplain SessionExpirationService} enqueues {@linkplain SessionInfo}
	 * objects upon receiving primary session map entry expiration events.
	 * 
	 * @author dpark
	 *
	 */
	class SessionInfo {
		SessionInfo(String sessionMapName, Object key) {
			this.sessionMapName = sessionMapName;
			this.key = key;
		}

		String sessionMapName;
		Object key;
	}

	/**
	 * {@linkplain SessionData} holds the tagged primary map name and its applicable
	 * data configured and determined during the
	 * {@linkplain SessionExpirationService} initialization time.
	 * 
	 * @author dpark
	 *
	 */
	class SessionData {
		SessionData(String taggedPrimaryMapName) {
			this.taggedPrimaryMapName = taggedPrimaryMapName;
		}

		String taggedPrimaryMapName;
		String[] relevantMapNames;
		String keyProperty;
		String getterMethodName;
		ISessionIdPredicate sessionIdPredicate;
		KeyType keyType = KeyType.STRING;
	}

	/**
	 * {@linkplain SessionTag} holds the session map name, tag value and
	 * {@linkplain SessionData} needed for determining the method to expire the
	 * entries from the relevant maps.
	 * 
	 * @author dpark
	 *
	 */
	class SessionTag {
		SessionTag(SessionData sessionData) {
			this.sessionData = sessionData;
		}

		String sessionMapName;
		String tag;
		SessionData sessionData;
	}
}
