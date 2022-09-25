package org.hazelcast.addon.cluster.expiration;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * {@linkplain SessionExpirationService_Predicate_In} is a singleton class that
 * expires all the specified session relevant entries from the pattern matching
 * maps.
 * <p>
 * <b>Experimental:</b>
 * <p>
 * This class is experimental only. Predicate "in" is slightly faster than "or".
 * 
 * @author dpark
 *
 */
public class SessionExpirationService_Predicate_In implements SessionExpirationServiceConfiguration {

	private final static SessionExpirationService_Predicate_In expirationService = new SessionExpirationService_Predicate_In();

	private ILogger logger = null;

	private ThreadGroup workerThreadGroup;
	private WorkerThread workerThreads[];
	private LinkedBlockingQueue<SessionInfo> workerQueues[];
	private HazelcastInstance hazelcastInstance;

	private String tag;
	private String logPrefix;
	private boolean isJmxEnabled;
	private SessionExpirationServiceStatus status;
	private boolean isJmxUseHazelcastObjectName;

	// delimiter is used for STRING key type only
	private String delimiter = DEFAULT_KEY_DELIMTER;

	// worker thread pool size
	private int threadPoolSize = DEFAULT_EXPIRATION_THREAD_POOL_SIZE;

	// queue drain size
	private int queueDrainSize = DEFAULT_EXPIRATION_QUEUE_DRAIN_SIZE;

	// Session ID as prefix or postfix. Default: prefix (for performance)
	private boolean isPostfix = false;

	// tagMap contains <tagged primary map name, SessionData> entries
	private HashMap<String, SessionData> tagMap = new HashMap<String, SessionData>(10);

	// sessionMaps contains <actual primary map name, SessionTag> entries
	private Map<String, SessionTag> sessionMap = new HashMap<String, SessionTag>(10);

	public final static SessionExpirationService_Predicate_In getExpirationService() {
		return expirationService;
	}

	private SessionExpirationService_Predicate_In() {
	}

	/**
	 * Initializes the session service.
	 * 
	 * @param properties
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	synchronized void initialize(Properties properties) {

		// Get the first HazelcastInstance
		Set<HazelcastInstance> set = Hazelcast.getAllHazelcastInstances();
		for (HazelcastInstance hz : set) {
			hazelcastInstance = hz;
			logger = hazelcastInstance.getLoggingService().getLogger(this.getClass());
			break;
		}

		// tag used for logging and JMX only. This tag is different from
		// NAME_TAG which applies to SessionTag.
		tag = properties.getProperty(PROPERTY_TAG, this.getClass().getSimpleName());
		logPrefix = tag + ": ";
		int index = PROPERTY_SESSION_PREFIX.length();
		String[] split = PROPERTY_SESSION_PREFIX.split("\\.");
		int primaryMapNamePrefixTokenCount = split.length + 1;
		StringBuffer buffer = new StringBuffer();
		for (Map.Entry entry : properties.entrySet()) {
			String key = (String) entry.getKey();
			if (key.startsWith(PROPERTY_SESSION_PREFIX)) {
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
						.getProperty(PROPERTY_SESSION_PREFIX + taggedPrimaryMapName + ".key.property");
				if (sessionData.keyProperty != null && sessionData.keyProperty.length() > 0) {
					// Change the first letter to upper case for constructing the get method.
					sessionData.getterMethodName = "get" + sessionData.keyProperty.substring(0, 1).toUpperCase()
							+ sessionData.keyProperty.substring(1);
				}

				// key.type
				String keyTypeStr = properties
						.getProperty(PROPERTY_SESSION_PREFIX + taggedPrimaryMapName + ".key.type");
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
							.getProperty(PROPERTY_SESSION_PREFIX + taggedPrimaryMapName + ".key.predicate");
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
		delimiter = properties.getProperty(PROPERTY_KEY_DELIMITER, DEFAULT_KEY_DELIMTER);

		// queueDrainSize
		String sizeStr = properties.getProperty(PROPERTY_EXPIRATION_QUEUE_DRAIN_SIZE);
		if (sizeStr != null) {
			try {
				queueDrainSize = Integer.parseInt(sizeStr);
			} catch (Exception ex) {
				logger.warning(logPrefix + ex.getMessage() + "[" + PROPERTY_EXPIRATION_QUEUE_DRAIN_SIZE + "=" + sizeStr
						+ "]. Using the default value of " + queueDrainSize + " instead.");
			}
		}

		String bool = properties.getProperty(PROPERTY_STRING_KEY_SESSION_POSTFIX_ENABLED, "false");
		isPostfix = bool.equalsIgnoreCase("true");

		bool = properties.getProperty(JMX_USE_HAZELCAST_OBJECT_NAME, "false");
		isJmxUseHazelcastObjectName = bool.equalsIgnoreCase("true");

		// Create thread pool
		String threadPoolSizeStr = properties.getProperty(PROPERTY_EXPIRATION_THREAD_POOL_SIZE);
		if (threadPoolSizeStr != null) {
			try {
				threadPoolSize = Integer.parseInt(threadPoolSizeStr);
			} catch (Exception ex) {
				logger.warning(logPrefix + ex.getMessage() + "[" + PROPERTY_EXPIRATION_THREAD_POOL_SIZE + "="
						+ threadPoolSizeStr + "]. Using the default value of " + threadPoolSize + " instead.");
			}
		}
		String threadGroupName = SessionExpirationService.class.getSimpleName();
		workerThreadGroup = new ThreadGroup(threadGroupName);
		workerThreadGroup.setDaemon(true);
		workerThreads = new WorkerThread[threadPoolSize];
		workerQueues = new LinkedBlockingQueue[threadPoolSize];
		for (int i = 0; i < threadPoolSize; i++) {
			workerQueues[i] = new LinkedBlockingQueue<SessionInfo>();
			WorkerThread workerThread = new WorkerThread(workerThreadGroup, "padogrid." + threadGroupName + "-" + (i + 1) /* thread name */,
					workerQueues[i]);
			workerThread.start();
			workerThreads[i] = workerThread;
		}

		if (logger != null) {
			logger.info(logPrefix + this.getClass().getCanonicalName() + " started: delimiter=\"" + delimiter + "\""
					+ ", threadPoolSize=" + threadPoolSize + ", queueDrainSize=" + queueDrainSize + ", isPostfix="
					+ isPostfix + ", isJmxUseHazelcastObjectName=" + isJmxUseHazelcastObjectName + " ["
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
					type = this.getClass().getSimpleName();
				}
				objectName = new ObjectName(header + ":name=SessionExpirationService"
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
	 * Returns the array index of the worker thread that has the smallest queue.
	 */
	private int getMinWorkerQueueIndex() {
		int minIndex = 0;
		LinkedBlockingQueue<SessionInfo> minQueue = workerQueues[minIndex];
		for (int i = 1; i < workerQueues.length; i++) {
			if (minQueue.size() > workerQueues[i].size()) {
				minIndex = i;
			}
		}
		return minIndex;
	}

	/**
	 * Returns the Hazelcast instance.
	 */
	public HazelcastInstance getHazelcastInstance() {
		return hazelcastInstance;
	}
	
	/**
	 * Expires all the entries from the configured maps that have the matching
	 * session ID extracted from the specified key.
	 * 
	 * @param sessionMapName The expiration originated map.
	 * @param key            Key object containing the session ID.
	 */
	public void expire(String sessionMapName, Object key) {
		if (key == null) {
			return;
		}
//		int index = Math.abs(key.hashCode()) % workerThreads.length;
		int index = getMinWorkerQueueIndex();
		if (workerThreads[index].isAlive() == false) {
			return;
		}

		SessionTag sessionTag = getSessionTag(sessionMapName);
		if (sessionTag == null) {
			return;
		}
		workerQueues[index].offer(new SessionInfo(sessionMapName, key));
		updateMBean();
	}

	/**
	 * Terminates the service thread. Note that it will not terminate if the
	 * underlying expiration event queue is empty. It will block until queue has at
	 * least one session ID to be processed.
	 */
	public void terminate() {
		for (int i = 0; i < workerThreads.length; i++) {
			workerThreads[i].terminate();
		}
	}

	/**
	 * Returns the underlying expiration event queue size.
	 * 
	 * @return
	 */
	public int getQueueSize() {
		int totalSize = 0;
		for (int i = 0; i < workerQueues.length; i++) {
			totalSize += workerQueues[i].size();

		}
		return totalSize;
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
		for (int i = 0; i < workerThreads.length; i++) {
			if (workerThreads[i].isTerminating()) {
				return true;
			}
		}
		return false;
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
	 * {@linkplain WorkerThread} takes {@linkplain SessionInfo} objects from the blocking
	 * queue and expires session entries from the relevant maps.
	 * 
	 * @author dpark
	 *
	 */
	class WorkerThread extends Thread {

		protected BlockingQueue<SessionInfo> queue = null;
		private boolean shouldRun = true;
		private boolean isTerminated = false;

		public WorkerThread(ThreadGroup threadGroup, String threadName, BlockingQueue<SessionInfo> queue) {
			super(threadGroup, threadName);
			this.queue = queue;
		}

		public WorkerThread(BlockingQueue<SessionInfo> queue) {
			this.queue = queue;
		}

		@SuppressWarnings("rawtypes")
		public void run() {
			while (shouldRun) {
				String sessionMapName = null;
				try {
					SessionInfo firstSessionInfo = queue.take();
					ArrayList<SessionInfo> sessionInfoList = new ArrayList<SessionInfo>();
					queue.drainTo(sessionInfoList, queueDrainSize);
					sessionInfoList.add(firstSessionInfo);
					updateMBean();

					HashMap<String, ArrayList<SessionInfo>> smap = new HashMap<String, ArrayList<SessionInfo>>();
					for (SessionInfo si : sessionInfoList) {
						ArrayList<SessionInfo> sessionInfoListPerMap = smap.get(si.sessionMapName);
						if (sessionInfoListPerMap == null) {
							sessionInfoListPerMap = new ArrayList<SessionInfo>();
							smap.put(si.sessionMapName, sessionInfoListPerMap);
						}
						sessionInfoListPerMap.add(si);
					}

					Map<String, IMap> allMapsInHazelcast = ClusterUtil.getAllMaps(hazelcastInstance);
					ArrayList<Comparable> compareableList = new ArrayList<Comparable>();
					for (Map.Entry<String, ArrayList<SessionInfo>> entry : smap.entrySet()) {
						sessionMapName = entry.getKey();
						ArrayList<SessionInfo> sessionInfoListPerMap = entry.getValue();

						SessionTag sessionTag = sessionMap.get(sessionMapName);
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
						Predicate innerPredicate = null;
						switch (sessionData.keyType) {
						case INTERFACE:
							for (SessionInfo sessionInfo : sessionInfoListPerMap) {
								if (sessionInfo.key instanceof ISessionId) {
									String sessionId = ((ISessionId) sessionInfo.key).getSessionId();
									compareableList.add(sessionId);
								}
							}
							if (compareableList.size() > 0) {
								predicate = Predicates.in("__key.sessionId",
										compareableList.toArray(new Comparable[compareableList.size()]));
							}
							break;

						case OBJECT:
							if (sessionData.keyProperty == null) {
								break;
							}
							for (SessionInfo sessionInfo : sessionInfoListPerMap) {
								try {
									Method method = sessionInfo.key.getClass().getMethod(sessionData.getterMethodName);
									Object sessionId = method.invoke(sessionInfo.key);
									if (sessionId != null) {
										compareableList.add(sessionId.toString());
									}
								} catch (Exception ex) {
									logger.warning(logPrefix + "Exception ocurred while processing " + KeyType.OBJECT
											+ " key type for expiring the relevant maps for "
											+ sessionInfo.sessionMapName + ".", ex);
								}
							}
							if (compareableList.size() > 0) {
								predicate = Predicates.in("__key." + sessionData.keyProperty,
										compareableList.toArray(new Comparable[compareableList.size()]));
							}
							break;

						case PARTITION_AWARE:
							for (SessionInfo sessionInfo : sessionInfoListPerMap) {
								Object partitionKey = ((PartitionAware) sessionInfo.key).getPartitionKey();
								if (partitionKey != null) {
									compareableList.add(partitionKey.toString());
								}
							}
							if (compareableList.size() > 0) {
								predicate = Predicates.in("__key.partitionKey",
										compareableList.toArray(new Comparable[compareableList.size()]));
							}
							break;

						case CUSTOM:
							for (SessionInfo sessionInfo : sessionInfoListPerMap) {
								// User OR for CUSTOM
								if (sessionData.sessionIdPredicate != null) {
									innerPredicate = sessionData.sessionIdPredicate
											.getPredicate(sessionInfo.sessionMapName, sessionInfo.key);
									if (predicate == null) {
										predicate = innerPredicate;
									} else if (innerPredicate != null) {
										predicate = Predicates.or(predicate, innerPredicate);
									}
								}
							}
							break;

						case STRING:
						default:
							// Use IN for STRING
							if (isPostfix) {
								for (SessionInfo sessionInfo : sessionInfoListPerMap) {
									/*
									 * The session ID is the last part of the key string separated by the delimiter.
									 * If the key does not contain the delimiter, then the entire key string is used
									 * as the session ID.
									 */
									String keyStr = sessionInfo.key.toString();
									int index = keyStr.lastIndexOf(delimiter);
									String sessionIdWithDelimiter;
									if (index == -1) {
										sessionIdWithDelimiter = keyStr;
									} else {
										sessionIdWithDelimiter = keyStr.substring(index);
									}
									if (sessionIdWithDelimiter.length() != 0) {
										innerPredicate = Predicates.like("__key", "%" + sessionIdWithDelimiter);
										if (predicate == null) {
											predicate = innerPredicate;
										} else if (innerPredicate != null) {
											predicate = Predicates.or(predicate, innerPredicate);
										}
									}
								}
							} else {
								for (SessionInfo sessionInfo : sessionInfoListPerMap) {
									/*
									 * The session ID is the firstß part of the key string separated by the delimiter.
									 * If the key does not contain the delimiter, then the entire key string is used
									 * as the session ID.
									 */
									String keyStr = sessionInfo.key.toString();
									int index = keyStr.indexOf(delimiter);
									String sessionIdWithDelimiter;
									if (index == -1) {
										sessionIdWithDelimiter = keyStr;
									} else {
										sessionIdWithDelimiter = keyStr.substring(0, index + 1);
									}
									if (sessionIdWithDelimiter.length() != 0) {
										innerPredicate = Predicates.like("__key", sessionIdWithDelimiter + "%");
										if (predicate == null) {
											predicate = innerPredicate;
										} else if (innerPredicate != null) {
											predicate = Predicates.or(predicate, innerPredicate);
										}
									}
								}
							}
							break;
						}

						// Remove entries by applying predicate. If predicate is null then unable to
						// expire entries in the relevant maps.
						if (predicate != null) {
							for (String relevantMapName : sessionData.relevantMapNames) {
								if (sessionTag.tag.length() > 0) {
									relevantMapName = relevantMapName.replaceAll(NAME_TAG, sessionTag.tag);
								}
								for (Map.Entry<String, IMap> entry2 : allMapsInHazelcast.entrySet()) {
									if (entry2.getKey().matches(relevantMapName)) {
										IMap map = entry2.getValue();
										MapUtil.removeMemberAllKeySet(map, predicate);
									}
								}
							}
							if (logger != null && logger.isFineEnabled()) {
								for (SessionInfo sessionInfo : sessionInfoList) {
									logger.fine(logPrefix + "Expired session: " + sessionInfo);
								}
							}
						}
					}
				} catch (Throwable ex) {
					logger.warning(logPrefix + "Exception occurred while applying predicate to expire relevant maps ["
							+ sessionMapName + "]", ex);
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
		 * Terminates the worker thread. Upon termination, the blocking queue will be
		 * cleared and the worker thread is no longer usable. Note that it will not
		 * terminate if the queue is empty. It will block until queue has at least one
		 * session ID to be processed.
		 */
		public void terminate() {
			shouldRun = false;
		}

		/**
		 * Returns true if the worker thread has been terminated.
		 * 
		 * @return
		 */
		public boolean isTerminated() {
			return isTerminated;
		}
	}

	/**
	 * {@linkplain SessionInfo} holds session map name and key.
	 * {@linkplain SessionExpirationService_Predicate_In} enqueues
	 * {@linkplain SessionInfo} objects upon receiving primary session map entry
	 * expiration events.
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
	 * {@linkplain SessionExpirationService_Predicate_In} initialization time.
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
