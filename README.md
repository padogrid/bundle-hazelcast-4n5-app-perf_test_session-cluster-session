# Session Expiration Management Plugin

This bundle provides a plugin that expires session objects in a given map and their relevant entries in other Hazelcast maps.

## Installing Bundle

```bash
install_bundle -download bundle-hazelcast-4n5-app-perf_test_session-cluster-session
```

## Use Case

You are storing user session objects in multiple Hazelcast maps. A single user session touches multiple maps and when the user is idle for some time, you want to end the session and remove all the entries that belong to that particular session from all the maps. To achieve this, you decide to use the session ID (typically UUID) as a prefix to all the keys that you are storing in the maps. You also configure the maps with `max-idle-seconds`, but since each map times out individually, the session entries would expire undeterministically. For example, some entries may expire earlier than others if there were no activities. But you want to be able deterministcally expire all the session entries only when the session times out.


## `SessionExpirationService` Plugin

The provided `SessionExpirationService` plugin solves this use case by adding an `EntryExpiredListener` to the primary map that gets updated for all session activities. If an entry expires in the primary map, then the listener expires (or removes) that entry's session relevant entries from all other maps. To lighten the load on the cluster, `SessionExpirationService` spawns a thread with a blocking queue that takes on removal tasks.

## Installation Steps

Run the `session` cluster's `build_app` script which sets the correct Hazelcast XML schema version in the `etc/hazelcast.xml` file. This step is not necessary if your workspace is running Hazelcast 4.x.

```bash
switch_cluster session/bin_sh
./build_app
```

## Startup Sequence

1. Start the `session` cluster.

```bash
switch_cluster session

# Add two (2) or more members in the cluster
add_member; add_member

# Start the session cluster and management center
start_cluster -all
```

2. Ingest data. The **mkp** maps are configured to timeout in 5 seconds and the **mkq** maps are configured to timeout in 10 seconds. See [hazelcast.yaml](clusters/session/etc/hazelcast.yaml).

```bash
cd_app perf_test_session/bin_sh

# Ingest data into smkp, mkp_*, mymkp maps.
./test_group -run -prop ../etc/group-mkp-put.properties

# Ingest data into smkq, mkq_*, mymkq maps.
./test_group -run -prop ../etc/group-mkq-put.properties
```

3. Monitor the maps from the management center.

URL: http://localhost:8080/hazelcast-mancenter

## Configuring `SessionExpirationService`

There are three (3) distinctive settings that must be included in the Hazelcast configuration file as follows.

1. Properties for defining the primary map, relevant maps, and session key delimiter.
2. `org.hazelcast.addon.cluster.expiration.SessionExpirationServiceInitializer` for initializing and starting the `SessionExpirationService` plugin.
3. `org.hazelcast.addon.cluster.expiration.SessionExpirationListener` for each primary map configured with `max-idle-seconds` or `time-to-live-seconds`.

| Property | Description | Default |
| -------- | ----------- | ------- |
| hazelcast.addon.cluster.expiration.tag | Tag used as a prefix to each log message and a part of JMX object name. | SessionExpirationService |
| hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name | If true, then the standard Hazelcast JMX object name is registered for the session expiration service. Hazelcast metrics are registered with the header “com.hazelcast” and “type=Metrics”. If false or unspecified, then object name is registered with the header “org.hazelcast.addon” and “type=SessionExpirationService”. | false |
| hazelcast.addon.cluster.expiration.session. | Property prefix for specifying a session map and the relevant maps. | N/A |
| hazelcast.addon.cluster.expiration.key.delimiter | Delimiter that separates the session ID and the remainder. | _ (underscore) |

Use the following configuration files as references.

- [hazelcast.yaml](clusters/session/etc/hazelcast.yaml)

```yaml
hazelcast:
  ...
  properties:
    hazelcast.phone.home.enabled: false
    hazelcast.addon.cluster.expiration.tag: SessionExpirationService
    hazelcast.addon.cluster.expiration.key.delimiter: _
    hazelcast.addon.cluster.expiration.session.smkp: mkp_.*,mymkp
    hazelcast.addon.cluster.expiration.session.smkq: mkq_.*,mymkq
    hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name: true
  listeners:
    # SessionExpirationServiceInitializer is invoked during bootstrap to
    # initialize and start SessionExpirationService.
    - org.hazelcast.addon.cluster.expiration.SessionExpirationServiceInitializer
  ...
  map:
    smkp:
      max-idle-seconds: 5
      entry-listeners:
      - class-name: org.hazelcast.addon.cluster.expiration.SessionExpirationListener
        include-value: false
    smkq:
      max-idle-seconds: 10
      entry-listeners:
      - class-name: org.hazelcast.addon.cluster.expiration.SessionExpirationListener
        include-value: false
```

- [hazelcast.xml](clusters/session/etc/hazelcast.xml)

```xml
<hazelcast ...>
	<properties>
		<property name="hazelcast.phone.home.enabled">false</property>
		<property name="hazelcast.addon.cluster.expiration.tag">SessionExpirationService</property>
		<property name="hazelcast.addon.cluster.expiration.key.delimiter">_</property>
		<property name="hazelcast.addon.cluster.expiration.session.smkp">mkp_.*,mymkp</property>
		<property name="hazelcast.addon.cluster.expiration.session.smkq">mkq_.*,mymkq</property>
		<property name="hazelcast.addon.cluster.expiration.jmx-use-hazelcast-object-name">true</property>
	</properties>   
    <listeners>
        <listener>
		org.hazelcast.addon.cluster.expiration.SessionExpirationServiceInitializer
		</listener>
    </listeners>
    ...
    <map name="smkp">
		<max-idle-seconds>5</max-idle-seconds>
		<entry-listeners>
			<entry-listener>org.hazelcast.addon.cluster.expiration.SessionExpirationListener</entry-listener>
		</entry-listeners>
	</map>
	<map name="smkq">
		<max-idle-seconds>10</max-idle-seconds>
		<entry-listeners>
			<entry-listener >org.hazelcast.addon.cluster.expiration.SessionExpirationListener</entry-listener>
		</entry-listeners>
	</map>
</hazelcast>
```
