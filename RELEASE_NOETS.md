# Session Expiration Service Release Notes

©2022 Netcrest Technologies, LLC. All rights reserved.
https://github.com/padogrid
https://github.com/padogrid/bundle-hazelcast-4n5-cluster-session

## Version 1.0.2

### Release Date: 05/31/22

- Optimized the expiration service by processing batches of events. There is room for further improvement. The current implementation still uses a single thread to handle events.
- Added key type descriptions.
- Added the [`SessionMetadata`](https://github.com/padogrid/bundle-hazelcast-4n5-cluster-session/blob/master/clusters/session/src/main/java/org/hazelcast/addon/cluster/expiration/metadata/SessionMetadata.java) option, which allows
client applications to provide relevant keys instead of the plugin determining them. This option sigficantly reduces the load on the cluster and the overall latency.
- The plugin fully supports sesson expirations over the WAN. This enables client applications to seamlessly failover to the secondary cluster with the session data fully intact, preventing session disruptions. 
- [**bundle-hazelcast-4n5-cluster-session-wan**](https://github.com/padogrid/bundle-hazelcast-4n5-cluster-session-wan) is now available for testing this plugin in a WAN environment.

## Version 1.0.1

### Release Date: 12/23/21

- SessionExpirationService now supports a variety of key types: CUSTOM, INTERFACE, OBJECT, PARTITION_AWARE, STRING. The previous version supported only STRING.
- This release replaces the delimiter, `_` (underscore) with `@` and the string key value must end with the session ID. These changes are made to comply with the Hazelcast sting key naming convention for co-locating data.
- Added support for composite keys.
- Replaced the `perf_test_session` app with the `test_session_ingestion` script.

---

## Version 1.0.0

### Release Date: 12/10/21

- Initial release for testing only. This is not fully tested for QA/Prod.
