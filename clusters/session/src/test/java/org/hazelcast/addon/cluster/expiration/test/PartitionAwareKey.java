package org.hazelcast.addon.cluster.expiration.test;

import java.io.Serializable;

import com.hazelcast.partition.PartitionAware;

@SuppressWarnings("rawtypes")
public class PartitionAwareKey implements PartitionAware, Serializable {

	private static final long serialVersionUID = 1L;

	private String sessionId;
	private String attribute;

	public PartitionAwareKey() {
	}

	public PartitionAwareKey(String sessionId, String attribute) {
		this.sessionId = sessionId;
		this.attribute = attribute;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getAttribute() {
		return attribute;
	}

	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}

	@Override
	public Object getPartitionKey() {
		return sessionId;
	}
	
	@Override
	public String toString() {
		return "PartitionAwareKey [sessionId=" + sessionId + ", attribute=" + attribute + "]";
	}
}
