package org.hazelcast.addon.cluster.expiration;

public class SessionExpirationServiceStatus implements SessionExpirationServiceStatusMBean {
	
	private Integer queueSize = 0;

	@Override
	public Integer getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(Integer queueSize) {
		this.queueSize = queueSize;
	}
}
