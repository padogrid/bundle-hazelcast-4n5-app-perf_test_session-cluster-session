package org.hazelcast.addon.cluster.expiration.test;

import java.io.Serializable;

public class CustomKey implements Serializable {

	private static final long serialVersionUID = 1L;

	private String sessionId;
	private String attribute;

	public CustomKey() {
	}

	public CustomKey(String sessionId, String attribute) {
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
	public String toString() {
		return "CustomKey [sessionId=" + sessionId + ", attribute=" + attribute + "]";
	}

}
