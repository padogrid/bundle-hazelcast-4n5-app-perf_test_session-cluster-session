package org.hazelcast.addon.cluster.expiration;

import java.lang.management.ManagementFactory;
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
	private BlockingQueue<SessionInfo> queue = new LinkedBlockingQueue<SessionInfo>();
	private HazelcastInstance hazelcastInstance;

	private String tag;
	private String logPrefix;
	private boolean isJmxEnabled;
	private SessionExpirationServiceStatus status;
	private boolean isJmxUseHazelcastObjectName;

	private String delimiter = SessionExpirationServiceConfiguration.DEFAULT_KEY_DELIMTER;
	private Map<String, String[]> sessionMaps = new HashMap<String, String[]>(4);

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

		tag = properties.getProperty(SessionExpirationServiceConfiguration.PROPERTY_TAG,
				SessionExpirationService.class.getSimpleName());
		int index = SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX.length();
		StringBuffer buffer = new StringBuffer();
		for (Map.Entry entry : properties.entrySet()) {
			String key = (String) entry.getKey();
			if (key.startsWith(SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX)) {
				String sessionMapName = key.substring(index);
				String mapNames = (String) entry.getValue();
				String[] names = mapNames.split(",");
				sessionMaps.put(sessionMapName, names);
				if (buffer.length() > 0) {
					buffer.append(", ");
				}
				buffer.append(sessionMapName + ": " + mapNames);
			}
		}
		delimiter = properties.getProperty(SessionExpirationServiceConfiguration.PROPERTY_KEY_DELIMITER,
				SessionExpirationServiceConfiguration.DEFAULT_KEY_DELIMTER);
		
		String bool = properties.getProperty(SessionExpirationServiceConfiguration.JMX_USE_HAZELCAST_OBJECT_NAME, "false");
		isJmxUseHazelcastObjectName = bool.equalsIgnoreCase("true");

		// Get the first HazelcastInstance
		Set<HazelcastInstance> set = Hazelcast.getAllHazelcastInstances();
		for (HazelcastInstance hz : set) {
			hazelcastInstance = hz;
			logger = hazelcastInstance.getLoggingService().getLogger(this.getClass());
			break;
		}

		// Start thread as daemon
		consumer = new Consumer(queue);
		Thread thread = new Thread(consumer, SessionExpirationService.class.getSimpleName());
		thread.setDaemon(true);
		thread.start();

		logPrefix = tag + ": ";
		if (logger != null) {
			logger.info(logPrefix + this.getClass().getCanonicalName() + " started: delimiter=\"" + delimiter + "\" [" + buffer.toString() + "]");
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
				logger.warning(logPrefix + SessionExpirationServiceStatusMBean.class.getSimpleName() + " registration error: tag=" + tag, ex);
			}
		}
	}

	/**
	 * Expires all the entries from the configured maps that have the matching
	 * session ID.
	 * 
	 * @param sessionMapName The expiration originated map.
	 * @param sessionId      Session ID to expire. The session ID is the prefix (or
	 *                       UUID) of all the keys to be removed from the relevant
	 *                       maps.
	 */
	public void expire(String sessionMapName, String sessionId) {
		sessionId = sessionId.substring(0, sessionId.indexOf(delimiter));
		queue.offer(new SessionInfo(sessionMapName, sessionId));
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
	 * still in termination state. It will terminate only when the current session
	 * ID (or the next session ID if the queue is empty) has been processed.
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
				try {
					SessionInfo sessionInfo = queue.take();
					String mapNames[] = sessionMaps.get(sessionInfo.sessionMapName);
					if (mapNames == null) {
						continue;
					}
					// Expire or remove all session specific entries from
					// all the maps.
					Predicate predicate = Predicates.like("__key", sessionInfo.sessionId + "%");
					Map<String, IMap> maps = ClusterUtil.getAllMaps(hazelcastInstance);
					for (String mapName : mapNames) {
						for (Map.Entry<String, IMap> entry : maps.entrySet()) {
							if (entry.getKey().matches(mapName)) {
								IMap map = entry.getValue();
								MapUtil.removeMemberAll(hazelcastInstance, map, predicate);
							}
						}
					}
					if (logger != null && logger.isFineEnabled()) {
						logger.fine(logPrefix + "Expired session: " + sessionInfo);
					}
				} catch (InterruptedException e) {
					// ignore
				}
			}
			isTerminated = true;
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
		 * Terminates the thread. Note that it will not terminate if the queue is empty.
		 * It will block until queue has at least one session ID to be processed.
		 */
		public void terminate() {
			shouldRun = false;
		}

		/**
		 * Returns true if the thread has been terminated.
		 * 
		 * @return
		 */
		public boolean isTerminated() {
			return isTerminated;
		}
	}

	/**
	 * SessionInfo holds session map name and ID.
	 * 
	 * @author dpark
	 *
	 */
	class SessionInfo {
		SessionInfo(String sessionMapName, String sessionId) {
			this.sessionMapName = sessionMapName;
			this.sessionId = sessionId;
		}

		String sessionMapName;
		String sessionId;

		@Override
		public String toString() {
			return "SessionInfo [sessionMapName=" + sessionMapName + ", sessionId=" + sessionId + "]";
		}
	}
}
