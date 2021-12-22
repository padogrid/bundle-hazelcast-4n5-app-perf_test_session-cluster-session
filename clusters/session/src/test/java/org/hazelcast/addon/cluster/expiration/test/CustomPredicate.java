package org.hazelcast.addon.cluster.expiration.test;

import org.hazelcast.addon.cluster.expiration.ISessionIdPredicate;
import org.hazelcast.addon.cluster.expiration.SessionExpirationService;

import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;

/**
 * {@linkplain CustomPredicate} is an example predicate provider that provides a
 * predicate for the session ID extracted from all the supported key types.
 * 
 * @author dpark
 *
 */
public class CustomPredicate implements ISessionIdPredicate {

	@SuppressWarnings("rawtypes")
	@Override
	public Predicate getPredicate(String primaryMapName, Object key) {
		if (key == null) {
			return null;
		}
		if (key instanceof CustomKey) {
			return Predicates.equal("__key.sessionId", ((CustomKey) key).getSessionId());
		} else if (key instanceof ObjectKey) {
			return Predicates.equal("__key.sessionId", ((ObjectKey) key).getSessionId());
		} else if (key instanceof InterfaceKey) {
			return Predicates.equal("__key.sessionId", ((InterfaceKey) key).getSessionId());
		} else if (key instanceof PartitionAwareKey) {
			return Predicates.equal("__key.sessionId", ((PartitionAwareKey) key).getSessionId());
		} else {
			String keyStr = key.toString();
			String delimiter = SessionExpirationService.getExpirationService().getDelimiter();
			int index = keyStr.lastIndexOf(delimiter);
			String sessionId;
			if (index == -1) {
				sessionId = keyStr;
			} else {
				sessionId = keyStr.substring(index + delimiter.length());
			}
			if (sessionId.length() != 0) {
				return Predicates.like("__key", "%" + sessionId);
			} else {
				return null;
			}
		}
	}

}
