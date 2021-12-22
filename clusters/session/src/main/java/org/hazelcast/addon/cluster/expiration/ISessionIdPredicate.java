package org.hazelcast.addon.cluster.expiration;

import com.hazelcast.query.Predicate;

/**
 * The primary session maps configured with the {@linkplain KeyType#CUSTOM} key
 * type can provide the custom predicate class by implementing this interface.
 * The fully-qualified implementation class name must be registered as with the
 * following property in the configuration file.
 * 
 * <pre>
 * hazelcast.addon.cluster.expiration.session.key.predicate
 * 
 * <pre>
 * 
 * @author dpark
 *
 */
public interface ISessionIdPredicate {

	/**
	 * Returns a predicate constructed for the {@linkplain KeyType#CUSTOM} key type.
	 * If it returns null, then the entries in the relevant maps will <b>not</b> be
	 * expired.
	 * 
	 * @param primaryMapName Name of the primary map that initiates entry
	 *                       expiration.
	 * @param key            Primary map key that expired.
	 */
	@SuppressWarnings("rawtypes")
	public Predicate getPredicate(String primaryMapName, Object key);
}
