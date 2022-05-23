package org.hazelcast.addon.cluster.expiration.metadata;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public class SessionMetadata implements IdentifiedDataSerializable {
	
	private HashMap<String, Object> map = new HashMap<String, Object>(10, 1f);
	
	public SessionMetadata() {
		
	}
	
	public void addRelevantKey(String mapName, Object key) {
		map.put(mapName, key);
	}
	
	public Object getRelevantKey(String mapName) {
		return map.get(mapName);
	}
	
	public Set<String> getMapNameSet() {
		return map.keySet();
	}
	
	public Set<Map.Entry<String, Object>> getEntrySet() {
		return map.entrySet();
	}

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
		Set<Map.Entry<String, Object>> set = map.entrySet();
		String[] keys = new String[set.size()];
		Object[] values = new Object[set.size()];
		int i = 0;
		for (Map.Entry<String, Object> entry : set) {
			keys[i] = entry.getKey();
			values[i]=entry.getValue();
			i++;
		}
		out.writeStringArray(keys);
		out.writeObject(values);
	}

	@Override
	public void readData(ObjectDataInput in) throws IOException {
		String[] keys = in.readStringArray();
		Object[] values = (Object[])in.readObject();
		for (int i = 0; i < keys.length; i ++) {
			map.put(keys[i], values[i]);
		}
	}

	@Override
	public int getFactoryId() {
		return ExpirationDataSerializableFactory.FACTORY_ID;
	}

	@Override
	public int getClassId() {
		return ExpirationDataSerializableFactory.SESSION_METADATA;
	}
}
