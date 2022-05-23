package org.hazelcast.addon.cluster.expiration.metadata;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.hazelcast.addon.cluster.expiration.KeyType;
import org.hazelcast.addon.cluster.expiration.SessionExpirationService;
import org.hazelcast.addon.cluster.expiration.SessionExpirationServiceConfiguration;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleListener;

/**
 * {@linkplain SessionExpirationServiceInitializer_SessionMetadata_Delete} initializes and starts
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
 * <th style="text-align:left">Default</th> </tr>
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
 * <td>hazelcast.addon.cluster.expiration.key.delimiter</td>
 * <td>Delimiter that separates key string and the sessionID. The sessionID is
 * always at the tail end of the string value.</td>
 * <td>@</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.queue.drain-size</td>
 * <td>Property for setting the expiration drain size. Each expiration event is
 * placed in a blocking queue that is drained by a separate consumer thread to
 * process them. The consumer thread drains the queue based on this value and
 * processes the expiration events in a batch at a time to provide better
 * performance. Note that a large drain size will throw stack overflow
 * exceptions for {@linkplain KeyType#STRING} which ends up building lengthy OR
 * predicates with LIKE conditions. Hazelcast appears to have undocumented
 * limitations on lengthy predicates.</td>
 * <td>100</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.string-key.postfix.enabled</td>
 * <td>Property for enabling or disabling session ID postfix for String keys.</td>
 * <td>false</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.session.</td>
 * <td>Property prefix for specifying a session map and the relevant maps.</td>
 * <td>N/A</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.session.foo%TAG%yong</td>
 * <td>Primary map name that begins with “foo” and ends with “yong” with the
 * pattern matcher %TAG% in between. This property’s value must be a comma
 * separated list of relevant map names with zero or more %TAG% and optional
 * regex. See examples below.</td>
 * <td>N/A</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.session.foo%TAG%yong.key.type</td>
 * <td>Key type. Valid types are CUSTOM, INTERFACE, OBJECT, PARTITION_AWARE, and
 * STRING.</td>
 * <td>STRING</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.session.foo%TAG%yong.key.property</td>
 * <td>Key property. The key class’ “get” method that returns the session
 * ID.</td>
 * <td>N/A</td>
 * </tr>
 * <tr>
 * <td>hazelcast.addon.cluster.expiration.session.foo%TAG%yong.key.predicate
 * </td>
 * <td>Predicate class name. Applies to the CUSTOM key type only.</td>
 * <td>N/A</td>
 * </tr>
 * </table>
 * <p>
 * <b>Notes:</b> %TAG% is a special replacement annotation that makes an exact
 * match of its position in the string value. Regular expression is supported
 * for listing relevant map names.
 * <p>
 * <b>Example 1:</b>
 * <p>
 * <pre>
 * hazelcast.addon.cluster.expiration.session.foo%TAG%yong: abc_%TAG%,xyz_%TAG%,mymap
 * </pre>
 * <p>
 * The above example matches the following map names.
 * <p>
 * <table border="1">
 * <tr>
 * <th style="text-align:left">Primary Map</th>
 * <th style="text-align:left">Relevant Maps</th>
 * </tr>
 * <tr>
 * <td>fooEN01yong</td>
 * <td>abc_EN01, xyz_EN01, mymap</td>
 * </tr>
 * <tr>
 * <td>fooEN02yong</td>
 * <td>abc_EN02, xyz_EN02, mymap</td>
 * </tr>
 * </table>
 * <p>
 * <b>Example 2 (regex):</b>
 * <p>
 * <pre>
 * hazelcast.addon.cluster.expiration.session.foo%TAG%yong: abc_%TAG%_.*_xyz
 * </pre>
 * <p>
 * The above example matches the following map names.
 * <p>
 * <table border="1">
 * <tr>
 * <th style="text-align:left">Primary Map</th>
 * <th style="text-align:left">Relevant Maps</th>
 * </tr>
 * <tr>
 * <td>fooEN01yong</td>
 * <td>abc_EN01_a_xyz, abc_EN01_ab_xyz, abc_EN01_aaaa_xyz</td>
 * </tr>
 * <tr>
 * <td>foo_EN02_yong</td>
 * <td>abc__EN02__a_xyz, abc__EN02__ab_xyz, abc__EN02__aaaa_xyz</td>
 * </tr>
 * </table>
 * 
 * @author dpark
 *
 */
public class SessionExpirationServiceInitializer_SessionMetadata_Delete implements LifecycleListener {

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
					if (key.startsWith(SessionExpirationServiceConfiguration.PROPERTY_EXPIRATION_PREFIX)) {
						serviceProperties.put(key, entry.getValue());
					}
				}
			}

			SessionExpirationService_SessionMetadata_Delete.getExpirationService().initialize(serviceProperties);
			break;

		default:
			break;
		}
	}
}
