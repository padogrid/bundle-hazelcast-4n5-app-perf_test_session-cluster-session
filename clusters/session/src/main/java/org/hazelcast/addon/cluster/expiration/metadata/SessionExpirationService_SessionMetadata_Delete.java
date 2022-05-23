package org.hazelcast.addon.cluster.expiration.metadata;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
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

import org.hazelcast.addon.cluster.expiration.ISessionIdPredicate;
import org.hazelcast.addon.cluster.expiration.KeyType;
import org.hazelcast.addon.cluster.expiration.SessionExpirationServiceConfiguration;
import org.hazelcast.addon.cluster.expiration.SessionExpirationServiceStatus;
import org.hazelcast.addon.cluster.expiration.SessionExpirationServiceStatusMBean;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.map.IMap;

/**
 * {@linkplain SessionExpirationService_SessionMetadata_Delete} is a singleton
 * class that expires all the specified session relevant entries from the
 * pattern matching maps by deleting entries.
 * 
 * @author dpark
 *
 */
public class SessionExpirationService_SessionMetadata_Delete implements SessionExpirationServiceConfiguration {

	private final static SessionExpirationService_SessionMetadata_Delete expirationService = new SessionExpirationService_SessionMetadata_Delete();

	private ILogger logger = null;

	private Consumer consumer;
	private Thread consumerThread;
	private BlockingQueue<SessionMetadata> queue = new LinkedBlockingQueue<SessionMetadata>();
	private HazelcastInstance hazelcastInstance;

	public HazelcastInstance getHazelcastInstance() {
		return hazelcastInstance;
	}

	private String tag;
	private String logPrefix;
	private boolean isJmxEnabled;
	private SessionExpirationServiceStatus status;
	// queue drain size
	private int queueDrainSize = DEFAULT_EXPIRATION_QUEUE_DRAIN_SIZE;
	// Session ID as prefix or postfix. Default: prefix (for performance)
	private boolean isPostfix = false;
	private boolean isJmxUseHazelcastObjectName;

	public final static SessionExpirationService_SessionMetadata_Delete getExpirationService() {
		return expirationService;
	}

	private SessionExpirationService_SessionMetadata_Delete() {
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
		// NAME_TAG which applies to SessionTag.
		tag = properties.getProperty(PROPERTY_TAG,
				SessionExpirationService_SessionMetadata_Delete.class.getSimpleName());
		logPrefix = tag + ": ";
		String bool = properties.getProperty(PROPERTY_STRING_KEY_SESSION_POSTFIX_ENABLED, "false");
		isPostfix = bool.equalsIgnoreCase("true");

		bool = properties.getProperty(JMX_USE_HAZELCAST_OBJECT_NAME, "false");
		isJmxUseHazelcastObjectName = bool.equalsIgnoreCase("true");

		// Start thread as daemon
		consumer = new Consumer(queue);
		consumerThread = new Thread(consumer, SessionExpirationService_SessionMetadata_Delete.class.getSimpleName());
		consumerThread.setDaemon(true);
		consumerThread.start();

		if (logger != null) {
			logger.info(logPrefix + this.getClass().getCanonicalName() + " started.");
		}

		// queueDrainSize
		String sizeStr = properties.getProperty(PROPERTY_EXPIRATION_QUEUE_DRAIN_SIZE);
		if (sizeStr != null) {
			try {
				queueDrainSize = Integer.parseInt(sizeStr);
			} catch (Exception ex) {
				logger.warning(logPrefix + ex.getMessage() + "[" + PROPERTY_EXPIRATION_QUEUE_DRAIN_SIZE + "=" + sizeStr
						+ "]. Using the default value of " + DEFAULT_EXPIRATION_QUEUE_DRAIN_SIZE + " instead.");
			}
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
					type = SessionExpirationService_SessionMetadata_Delete.class.getSimpleName();
				}
				objectName = new ObjectName(
						header + ":name=" + SessionExpirationService_SessionMetadata_Delete.class.getSimpleName()
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
	 * Expires all the entries from the configured maps that have the matching
	 * session ID extracted from the specified key.
	 * 
	 * @param sessionMapName The expiration originated map.
	 * @param key            Key object containing the session ID.
	 */
	public void expire(String sessionMapName, SessionMetadata sm) {
		if (consumerThread.isAlive() == false) {
			return;
		}
		if (sm == null) {
			return;
		}
		queue.offer(sm);
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

		protected BlockingQueue<SessionMetadata> queue = null;
		private boolean shouldRun = true;
		private boolean isTerminated = false;

		public Consumer(BlockingQueue<SessionMetadata> queue) {
			this.queue = queue;
		}

		public void run() {
			while (shouldRun) {
				String sessionMapName = null;
				try {
					SessionMetadata firstSessionInfo = queue.take();
					ArrayList<SessionMetadata> smList = new ArrayList<SessionMetadata>();
					queue.drainTo(smList, queueDrainSize);
					smList.add(firstSessionInfo);
					updateMBean();

					for (SessionMetadata sm : smList) {
						Set<Map.Entry<String, Object>> set = sm.getEntrySet();
						for (Map.Entry<String, Object> entry : set) {
							String mapName = entry.getKey();
							Object key = entry.getValue();
							IMap<?, ?> map = hazelcastInstance.getMap(mapName);
							map.delete(key);
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
	 * {@linkplain SessionExpirationService_SessionMetadata_Delete} enqueues
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
	 * {@linkplain SessionExpirationService_SessionMetadata_Delete} initialization
	 * time.
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
