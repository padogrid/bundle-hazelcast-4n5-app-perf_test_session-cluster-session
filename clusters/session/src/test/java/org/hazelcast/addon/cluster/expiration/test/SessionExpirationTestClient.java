package org.hazelcast.addon.cluster.expiration.test;

import java.util.UUID;

import org.hazelcast.addon.cluster.expiration.KeyType;
import org.hazelcast.addon.cluster.expiration.SessionExpirationService;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * {@linkplain SessionExpirationTestClient} is a client driver for ingesting
 * session data into a primary {@linkplain IMap} and its relevant maps serviced by
 * {@linkplain SessionExpirationService}. 
 * 
 * @author dpark
 *
 */
public class SessionExpirationTestClient {

	public final static String PROPERTY_executableName = "executable.name";

	KeyType keyType;
	int count;
	String primaryMapName;
	String[] relevantMapNames;
	HazelcastInstance hzInstance;

	SessionExpirationTestClient(KeyType keyType, int count, String primaryMapName, String[] relevantMapNames) {
		this.keyType = keyType;
		this.count = count;
		this.primaryMapName = primaryMapName;
		this.relevantMapNames = relevantMapNames;
		this.hzInstance = HazelcastClient.newHazelcastClient();
	}

	@SuppressWarnings("rawtypes")
	void ingestData() {
		System.out.println("Ingesting data [" + keyType + "]...");
		IMap primaryMap = hzInstance.getMap(primaryMapName);
		IMap[] relevantMaps = new IMap[relevantMapNames.length];
		for (int i = 0; i < relevantMapNames.length; i++) {
			relevantMaps[i] = hzInstance.getMap(relevantMapNames[i]);
		}

		// value object
		byte[] blob = new byte[100];
		for (byte i = 0; i < blob.length; i++) {
			blob[i] = i;
		}

		switch (keyType) {
		case INTERFACE:
			ingestInterfaceKey(primaryMap, relevantMaps, blob);
			break;
		case OBJECT:
			ingestObjectKey(primaryMap, relevantMaps, blob);
			break;
		case CUSTOM:
			ingestCustomKey(primaryMap, relevantMaps, blob);
			break;
		case PARTITION_AWARE:
			ingestPartitionAwareyKey(primaryMap, relevantMaps, blob);
			break;
		case STRING:
		default:
			ingestStringKey(primaryMap, relevantMaps, blob);
			break;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void ingestInterfaceKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			InterfaceKey key = new InterfaceKey(sessionId, attribute);
			map.put(key, blob);
			for (IMap rmap : relevantMaps) {
				rmap.put(key, blob);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void ingestObjectKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = i + "";
			ObjectKey key = new ObjectKey(sessionId, attribute);
			map.put(key, blob);
			for (IMap rmap : relevantMaps) {
				rmap.put(key, blob);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void ingestCustomKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			CustomKey key = new CustomKey(sessionId, attribute);
			map.put(key, blob);
			for (IMap rmap : relevantMaps) {
				rmap.put(key, blob);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void ingestPartitionAwareyKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			PartitionAwareKey key = new PartitionAwareKey(sessionId, attribute);
			map.put(key, blob);
			for (IMap rmap : relevantMaps) {
				rmap.put(key, blob);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void ingestStringKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			String key = attribute + "@" + sessionId;
			map.put(key, blob);
			for (IMap rmap : relevantMaps) {
				rmap.put(key, blob);
			}
		}
	}

	void shutdown() {
		hzInstance.shutdown();
	}

	private static void usage() {
		String executableName = System.getProperty(PROPERTY_executableName,
				SessionExpirationTestClient.class.getName());
		writeLine();
		writeLine("NAME");
		writeLine("   " + executableName + " - Ingest mock data into the specified maps for testing the session expiration plugin, "
				+ SessionExpirationService.class.getName());
		writeLine();
		writeLine("SYNOPSIS");
		writeLine("   " + executableName + " -primary primary_map_name -relevant relevant_map_names");
		writeLine("               [-type CUSTOM|INTERFACE|OBJECT|PARTITION_AWARE|STRING] [-count count] [-?]");
		writeLine();
		writeLine("DESCRIPTION");
		writeLine("   Ingests mock data into the specified maps for testing the session expiration plugin, ");
		writeLine("   " + SessionExpirationService.class.getName() + ". The examples shown below");
		writeLine("   are based on the member configuration files (etc/hazelcast.yaml and etc/hazelcast.xml).");
		writeLine();
		writeLine("OPTIONS");
		writeLine("   -primary primary_map_name");
		writeLine("             Primary (or session) map name. Entry expiration in this map triggers all relevant");
		writeLine("             maps to also expire.");
		writeLine();
		writeLine("   -relevant relevant_map_names");
		writeLine("             Comma separated relevant maps. In addition to the primary map, mock data will be");
		writeLine("             also ingested in these maps.");
		writeLine();
		writeLine("   -type CUSTOM|INTERFACE|OBJECT|PARTITION_AWARE|STRING");
		writeLine("             Key type (case-insensitive). If not specified, then defaults to STRING.");
		writeLine("             CUSTOM for CustomKey and CustomExtractor, INTERFACE for InterfaceKey");
		writeLine("             OBJECT for ObjectKey, and PARTITION_AWARE for PartitionAwareKey");
		writeLine();
		writeLine("   -count count");
		writeLine("             Number of entries to ingest. If not specified, defaults to 100");
		writeLine();
		writeLine("EXAMPLES");
		writeLine("   # INTERFACE: Ingest InterfaceKey that implements ISessionId into");
		writeLine("   #            smki_EN01, mki1_EN01, mki2_EN02");
		writeLine("   ./" + executableName + " -type INTERFACE -primary smki_EN01 -relevant mki1_EN01,mki2_EN01");
		writeLine();
		writeLine("   # OBJECT: Ingest objects (ObjectKey) with the getSessionId() method into");
		writeLine("   #         smko_EN01, mko1_EN01, mko2_EN02");
		writeLine("   # The key property, sessionId, is specified in the cluster config file.");
		writeLine("   ./test_session_ingestion -type OBJECT -primary smko_EN01 -relevant mko1_EN01,mko2_EN01");
		writeLine();
		writeLine("   # CUSTOM: Ingest CustomKey objects into");
		writeLine("   #         smkc_EN01, mkc_EN01, mkc2_EN02");
		writeLine("   # SessionID is extracted by CustomPrediate specified in the cluster config file.");
		writeLine("   ./" + executableName + " -type CUSTOM -primary smkc_EN01 -relevant mkc1_EN01,mkc2_EN01");
		writeLine();
		writeLine("   # PARTITION_AWARE: Ingest PartitionAwareKey that implements PartitionAware into");
		writeLine("   #        smkp_EN01, mkp1_EN01, mkp2_EN02");
		writeLine("   #        It uses PartitionAware.getPartionKey() as the session ID.");
		writeLine("   ./test_session_ingestion -type PARTITION_AWARE -primary smkp_EN01 -relevant mkp1_EN01,mkp2_EN01");
		writeLine();
		writeLine("   # STRING: Ingest String keys into");
		writeLine("   #         smks_EN01, mks_EN01, mks2_EN02");
		writeLine("   # SessionID is extracted from key objects using the specified delimiter in the cluster");
		writeLine("   # config file. The default delimiter is '@' and the session ID is the last part of the key.");
		writeLine("   ./" + executableName + " -type STRING -primary smks_EN01 -relevant mks1_EN01,mks2_EN01");
		writeLine();
		writeLine("   # OBJECT: Ingest objects (ObjectKey) with the getSessionId() method into");
		writeLine("   #         mkp_session_web_session_fi_session_id_mapping_EN0,");
		writeLine("   #         mmkp_session_fi_session_data_EN01,");
		writeLine("   #         mkp_session_application_data_EN0");
		writeLine("   # The key property, sessionId, is specified in the cluster config file.");
		writeLine("   ./test_session_ingestion -type OBJECT -primary mkp_session_web_session_fi_session_id_mapping_EN01 -relevant mkp_session_fi_session_data_EN01,mkp_session_application_data_EN01");
		writeLine();
	}

	private static void writeLine() {
		System.out.println();
	}

	private static void writeLine(String line) {
		System.out.println(line);
	}

	public static void main(String[] args) {
		String keyTypeStr = "STRING";
		String primaryMapName = null;
		String relevantMapNames = null;
		int count = 100;
		String arg;
		for (int i = 0; i < args.length; i++) {
			arg = args[i];
			if (arg.equalsIgnoreCase("-?")) {
				usage();
				System.exit(0);
			} else if (arg.startsWith("-type")) {
				if (i < args.length - 1) {
					keyTypeStr = args[++i].trim();
				}
			} else if (arg.startsWith("-primary")) {
				if (i < args.length - 1) {
					primaryMapName = args[++i].trim();
				}
			} else if (arg.startsWith("-relevant")) {
				if (i < args.length - 1) {
					relevantMapNames = args[++i].trim();
				}
			} else if (arg.startsWith("-count")) {
				if (i < args.length - 1) {
					String countStr = args[++i].trim();
					try {
						count = Integer.parseInt(countStr);
					} catch (Exception ex) {
						System.err.println("ERROR: Invalid count [" + countStr + "]. Command aborted.");
						System.exit(1);
					}
				}
			}
		}
		if (primaryMapName == null) {
			System.err.println("ERROR: Primary map name not specified. Command aborted.");
			System.exit(1);
		}
		if (relevantMapNames == null) {
			System.err.println("ERROR: Relevant map name(s) not specified. Command aborted.");
			System.exit(2);
		}
		KeyType keyType = KeyType.STRING;
		try {
			keyType = KeyType.valueOf(keyTypeStr.toUpperCase());
		} catch (Exception ex) {
			System.err.println("ERROR: Invalid key type [" + keyTypeStr + "]. Command aborted.");
			System.exit(3);
		}
		if (count <= 0) {
			System.err.println("ERROR: Invalid count [" + count + "]. Must be greater than 0. Command aborted.");
			System.exit(4);
		}

		String[] relevantMapNameArray = relevantMapNames.split(",");
		SessionExpirationTestClient client = new SessionExpirationTestClient(keyType, count, primaryMapName,
				relevantMapNameArray);
		client.ingestData();
		client.shutdown();
	}

}
