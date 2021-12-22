package org.hazelcast.addon.cluster.expiration.test;

import java.io.Serializable;

import org.hazelcast.addon.cluster.expiration.ISessionId;
import org.hazelcast.addon.cluster.expiration.SessionExpirationServiceConfiguration;

public class InterfaceKey implements ISessionId, Serializable {

	private static final long serialVersionUID = 1L;

	private String sessionId;
	private String attribute;

	public InterfaceKey() {
	}

	public InterfaceKey(String sessionId, String attribute) {
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
		return attribute + SessionExpirationServiceConfiguration.DEFAULT_KEY_DELIMTER + sessionId;
	}
}
