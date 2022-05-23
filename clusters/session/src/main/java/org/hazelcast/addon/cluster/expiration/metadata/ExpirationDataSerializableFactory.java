package org.hazelcast.addon.cluster.expiration.metadata;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class ExpirationDataSerializableFactory implements DataSerializableFactory {

	public static final int FACTORY_ID = 1010;

	public static final int SESSION_METADATA = 1010;

	@Override
	public IdentifiedDataSerializable create(int typeId) {
		switch (typeId) {
		case SESSION_METADATA:
			return new SessionMetadata();
			
		default:
			return null;
		}
	}

}
