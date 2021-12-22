package org.hazelcast.addon.cluster.expiration;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;

/**
 * {@linkplain SessionExpirationServiceInitializer} initializes and starts
 * {@linkplain SessionExpirationService} with the session properties extracted
 * from the Hazelcast configuration.
 * <p>
 * The following property prefix must be used to specify to expire entries in
 * the relevant maps.
 * 
 * <pre>
 * hazelcast.addon.cluster.expiration.session.
 * </pre>
 * 
 * For example, the following property expects the primary map, <b>smkp</b>, to
 * have an expiration policy configured and expires (or removes) all entries
 * that have the same session key prefix in the relevant maps, <b>mkp_.*</b> and
 * <b>mymkp</b>. The relevant map names must be comma separated and regular
 * expression is supported, e.g., <b>mkp_.*</b> indicates any map names that
 * begin with <i>mkp_</i>.
 * <p>
 * 
 * <pre>
 * &lt;properties&gt;
 *    &lt;property name="hazelcast.addon.cluster.expiration.tag"&gt;SessionExpirationService&lt;/property&gt;
 *    &lt;property name="hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name"&gt;true&lt;/property&gt;
 *    &lt;property name="hazelcast.addon.cluster.expiration.session.smkp"&gt;mkp_.*,mymkp&lt;/property&gt;
 * &lt;properties&gt;
 *    &lt;property name="hazelcast.addon.cluster.expiration.key.delimiter"&gt;@&lt;/property&gt;
 * 
 * &lt;listeners&gt;
 *    &lt;listener&gt;
 *    org.hazelcast.addon.cluster.expiration.SessionExpirationServiceInitializer
 *    &lt;/listener&gt;
 * &lt;/listeners&gt;
 * 
 * &lt;map name="smkp"&gt;
 *    &lt;max-idle-seconds&gt;600&lt;/max-idle-seconds&gt;
 *    &lt;entry-listeners&gt;
 *       &lt;entry-listener&gt;org.hazelcast.addon.cluster.expiration.SessionExpirationListener&lt;/entry-listener>
 *    &lt;/entry-listeners&gt;
 * &lt;/map&gt;
 * </pre>
 * 
 * In addition to regular expressions, the annotation, %TAG%, can be used to
 * identify string patterns.
 * 
 * <pre>
 * hazelcast.addon.cluster.expiration.session.smki_%TAG%: mki1_%TAG%,mki2_%TAG%
 * </pre>
 * 
 * For example, the above property matches the following maps.
 * 
 * <pre>
 * smki_EN01: mki1_EN01,mki2_EN01
 * smki_abc_EN02: mki1_xyz_EN02,hello_EN02
 * </pre>
 * 
 * The following system properties are available for configuring
 * {@linkplain SessionExpirationService}.
 * <p>
 * <table border="1">
 * <tr>
 * <th style="text-align:left">Property</th>
 * <th style="text-align:left">Description</th>
 * <th style="text-align:left">Default</th> </tr
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.tag</td>
 * <td>Tag used as a prefix to each log message and a part of JMX object
 * name.</td>
 * <td>SessionExpirationService</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name</td>
 * <td>If true, then the standard Hazelcast JMX object name is registered for
 * the session expiration service. Hazelcast metrics are registered with the
 * header “com.hazelcast” and “type=Metrics”. If false or unspecified, then
 * object name is registered with the header “org.hazelcast.addon” and
 * “type=SessionExpirationService”.</td>
 * <td>false</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.session.</td>
 * <td>Property prefix for specifying a session map and the relevant maps.</td>
 * <td>N/A</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.key.delimiter</td>
 * <td>Delimiter separating the session ID from the key value. The last token is the session ID.</td>
 * <td>@</td>
 * </tr>
 * </table>
 * @author dpark
 *
 */
public class SessionExpirationServiceInitializer implements LifecycleListener {

	@SuppressWarnings("rawtypes")
	@Override
	public void stateChanged(LifecycleEvent event) {
		Properties serviceProperties = new Properties();
		switch (event.getState()) {
		case STARTED:
			Set<HazelcastInstance> set = Hazelcast.getAllHazelcastInstances();
			for (HazelcastInstance hazelcastInstance : set) {
				Config config = hazelcastInstance.getConfig();

				for (Map.Entry entry : config.getProperties().entrySet()) {
					String key = (String) entry.getKey();
					if (key.startsWith(SessionExpirationServiceConfiguration.PROPERTY_SESSION_PREFIX)) {
						serviceProperties.put(key, entry.getValue());
					}
				}
			}

			SessionExpirationService.getExpirationService().initialize(serviceProperties);
			break;

		default:
			break;
		}
	}
}
