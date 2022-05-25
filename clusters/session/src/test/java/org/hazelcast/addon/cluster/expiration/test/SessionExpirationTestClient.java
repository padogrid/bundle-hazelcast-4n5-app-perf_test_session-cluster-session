package org.hazelcast.addon.cluster.expiration.test;

import java.text.NumberFormat;
import java.util.UUID;

import org.hazelcast.addon.cluster.expiration.KeyType;
import org.hazelcast.addon.cluster.expiration.SessionExpirationService;
import org.hazelcast.addon.cluster.expiration.metadata.SessionMetadata;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * {@linkplain SessionExpirationTestClient} is a client driver for ingesting
 * session data into a primary {@linkplain IMap} and its relevant maps serviced
 * by {@linkplain SessionExpirationService}.
 * 
 * @author dpark
 *
 */
public class SessionExpirationTestClient {

	public final static String PROPERTY_executableName = "executable.name";

	enum EntryType {
		INGEST, PUT, RESET, GET, METADATA
	}

	KeyType keyType;
	int count;
	String primaryMapName;
	String[] relevantMapNames;
	EntryType entryType;
	String sessionId;
	String attribute;
	HazelcastInstance hzInstance;

	SessionExpirationTestClient(KeyType keyType, int count, String primaryMapName, String[] relevantMapNames,
			EntryType entryType, String sessionId, String attribute) {
		this.keyType = keyType;
		this.count = count;
		this.primaryMapName = primaryMapName;
		this.relevantMapNames = relevantMapNames;
		this.entryType = entryType;
		this.sessionId = sessionId;
		this.attribute = attribute;
		this.hzInstance = HazelcastClient.newHazelcastClient();
	}

	@SuppressWarnings("rawtypes")
	void ingestData(boolean isMetadata, boolean isPostfix) {
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
		
		if (keyType == KeyType.STRING) {
			if (isPostfix) {
				writeLine("String key(s) with session ID as postfix: attribute@sessionId");
			} else {
				writeLine("String key(s) with session ID as prefix: sessionId@attribute");
			}
		}

		if (isMetadata) {
			switch (entryType) {
			case GET:
				Object value;
				if (isPostfix) {
					value = getStringKey_SessionId_Postfix(primaryMap, sessionId, attribute);
				} else {
					value = getStringKey_SessionId_Prefix(primaryMap, sessionId, attribute);
				}
				writeLine("Get value: " + value);
				break;
			case PUT:
				if (isPostfix) {
					setStringKey_Postfix(primaryMap, relevantMaps, sessionId, attribute, blob);
				} else {
					setStringKey_Prefix(primaryMap, relevantMaps, sessionId, attribute, blob);
				}
				break;
			case RESET:
			default:
				if (isPostfix) {
					resetStringKey_SessionMap_SessionId_Postfix(primaryMap, relevantMaps, sessionId, attribute);
				} else {
					resetStringKey_SessionMap_SessionId_Prefix(primaryMap, relevantMaps, sessionId, attribute);
				}
				break;
			}
			return;
		}

		switch (entryType) {
		case GET:
			writeLine("Putting entry [" + keyType + "]...");
			Object value = null;
			switch (keyType) {
			case INTERFACE:
				value = getInterfaceKey(primaryMap, sessionId, attribute);
				break;
			case OBJECT:
				value = getObjectKey(primaryMap, sessionId, attribute);
				break;
			case CUSTOM:
				value = getCustomKey(primaryMap, sessionId, attribute);
				break;
			case PARTITION_AWARE:
				value = getPartitionAwareyKey(primaryMap, sessionId, attribute);
				break;
			case STRING:
			default:
				if (isPostfix) {
					value = getStringKey_SessionId_Postfix(primaryMap, sessionId, attribute);
				} else {
					value = getStringKey_SessionId_Prefix(primaryMap, sessionId, attribute);
				}
				break;
			}
			writeLine("Get value: " + value);
			break;

		case PUT:
			writeLine("Putting entry [" + keyType + "]...");
			switch (keyType) {
			case INTERFACE:
				setInterfaceKey(primaryMap, relevantMaps, sessionId, attribute, blob);
				break;
			case OBJECT:
				setObjectKey(primaryMap, relevantMaps, sessionId, attribute, blob);
				break;
			case CUSTOM:
				setCustomKey(primaryMap, relevantMaps, sessionId, attribute, blob);
				break;
			case PARTITION_AWARE:
				setPartitionAwareyKey(primaryMap, relevantMaps, sessionId, attribute, blob);
				break;
			case STRING:
			default:
				if (isPostfix) {
					setStringKey_Postfix(primaryMap, relevantMaps, sessionId, attribute, blob);
				} else {
					setStringKey_Prefix(primaryMap, relevantMaps, sessionId, attribute, blob);
				}
				break;
			}
			break;

		case RESET:
			writeLine("Resetting entry [" + keyType + "]...");
			switch (keyType) {
			case INTERFACE:
				resetInterfaceKey(primaryMap, sessionId, attribute, blob);
				break;
			case OBJECT:
				resetObjectKey(primaryMap, sessionId, attribute, blob);
				break;
			case CUSTOM:
				resetCustomKey(primaryMap, sessionId, attribute, blob);
				break;
			case PARTITION_AWARE:
				resetPartitionAwareyKey(primaryMap, sessionId, attribute, blob);
				break;
			case STRING:
			default:
				if (isPostfix) {
					resetStringKey_SessionId_Postfix(primaryMap, sessionId, attribute, blob);
				} else {
					resetStringKey_SessionId_Prefix(primaryMap, sessionId, attribute, blob);
				}
				break;
			}
			break;

		case INGEST:
		default:
			writeLine("Ingesting data [" + keyType + "]...");
			long startTime = System.currentTimeMillis();
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
				if (isPostfix) {
					ingestStringKey_SessionId_Postfix(primaryMap, relevantMaps, blob);
				} else {
					ingestStringKey_SessionId_Prefix(primaryMap, relevantMaps, blob);
				}
				break;
			}
			long timeTook = System.currentTimeMillis() - startTime;
			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(2);
			double tsd = (double) timeTook / 1000d;
			double tsm = (double) timeTook / 60000d;
			writeLine("Time took (msec): " + timeTook);
			writeLine(" Time took (sec): " + nf.format(tsd));
			writeLine(" Time took (min): " + nf.format(tsm));
			break;

		}

	}

	@SuppressWarnings({ "rawtypes" })
	Object getInterfaceKey(IMap map, String sessionId, String attribute) {
		InterfaceKey key = new InterfaceKey(sessionId, attribute);
		return map.get(key);
	}

	@SuppressWarnings({ "rawtypes" })
	Object getObjectKey(IMap map, String sessionId, String attribute) {
		ObjectKey key = new ObjectKey(sessionId, attribute);
		return map.get(key);
	}

	@SuppressWarnings({ "rawtypes" })
	Object getCustomKey(IMap map, String sessionId, String attribute) {
		CustomKey key = new CustomKey(sessionId, attribute);
		return map.get(key);
	}

	@SuppressWarnings({ "rawtypes" })
	Object getPartitionAwareyKey(IMap map, String sessionId, String attribute) {
		PartitionAwareKey key = new PartitionAwareKey(sessionId, attribute);
		return map.get(key);
	}

	@SuppressWarnings({ "rawtypes" })
	Object getStringKey_SessionId_Postfix(IMap map, String sessionId, String attribute) {
		String key = attribute + "@" + sessionId;
		return map.get(key);
	}

	@SuppressWarnings({ "rawtypes" })
	Object getStringKey_SessionId_Prefix(IMap map, String sessionId, String attribute) {
		String key = sessionId + "@" + attribute;
		return map.get(key);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetInterfaceKey(IMap map, String sessionId, String attribute, byte[] blob) {
		InterfaceKey key = new InterfaceKey(sessionId, attribute);
		map.set(key, blob);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetObjectKey(IMap map, String sessionId, String attribute, byte[] blob) {
		ObjectKey key = new ObjectKey(sessionId, attribute);
		map.set(key, blob);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetCustomKey(IMap map, String sessionId, String attribute, byte[] blob) {
		CustomKey key = new CustomKey(sessionId, attribute);
		map.set(key, blob);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetPartitionAwareyKey(IMap map, String sessionId, String attribute, byte[] blob) {
		PartitionAwareKey key = new PartitionAwareKey(sessionId, attribute);
		map.set(key, blob);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetStringKey_SessionId_Postfix(IMap map, String sessionId, String attribute, byte[] blob) {
		String key = attribute + "@" + sessionId;
		map.set(key, blob);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetStringKey_SessionId_Prefix(IMap map, String sessionId, String attribute, byte[] blob) {
		String key = sessionId + "@" + attribute;
		map.set(key, blob);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetStringKey_SessionMap_SessionId_Postfix(IMap map, IMap[] relevantMaps, String sessionId,
			String attribute) {
		String key = attribute + "@" + sessionId;
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void resetStringKey_SessionMap_SessionId_Prefix(IMap map, IMap[] relevantMaps, String sessionId, String attribute) {
		String key = sessionId + "@" + attribute;
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void setInterfaceKey(IMap map, IMap[] relevantMaps, String sessionId, String attribute, byte[] blob) {
		InterfaceKey key = new InterfaceKey(sessionId, attribute);
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
		for (IMap rmap : relevantMaps) {
			rmap.set(key, blob);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void setObjectKey(IMap map, IMap[] relevantMaps, String sessionId, String attribute, byte[] blob) {
		ObjectKey key = new ObjectKey(sessionId, attribute);
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
		for (IMap rmap : relevantMaps) {
			rmap.set(key, blob);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void setCustomKey(IMap map, IMap[] relevantMaps, String sessionId, String attribute, byte[] blob) {
		CustomKey key = new CustomKey(sessionId, attribute);
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
		for (IMap rmap : relevantMaps) {
			rmap.set(key, blob);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void setPartitionAwareyKey(IMap map, IMap[] relevantMaps, String sessionId, String attribute, byte[] blob) {
		PartitionAwareKey key = new PartitionAwareKey(sessionId, attribute);
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
		for (IMap rmap : relevantMaps) {
			rmap.set(key, blob);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void setStringKey_Postfix(IMap map, IMap[] relevantMaps, String sessionId, String attribute, byte[] blob) {
		String key = attribute + "@" + sessionId;
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
		for (IMap rmap : relevantMaps) {
			rmap.set(key, blob);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	void setStringKey_Prefix(IMap map, IMap[] relevantMaps, String sessionId, String attribute, byte[] blob) {
		String key = sessionId + "@" + attribute;
		SessionMetadata sm = new SessionMetadata();
		for (IMap rmap : relevantMaps) {
			sm.addRelevantKey(rmap.getName(), key);
		}
		map.set(key, sm);
		for (IMap rmap : relevantMaps) {
			rmap.set(key, blob);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	void ingestInterfaceKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			setInterfaceKey(map, relevantMaps, sessionId, attribute, blob);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	void ingestObjectKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = i + "";
			setObjectKey(map, relevantMaps, sessionId, attribute, blob);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	void ingestCustomKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			setCustomKey(map, relevantMaps, sessionId, attribute, blob);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	void ingestPartitionAwareyKey(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			setPartitionAwareyKey(map, relevantMaps, sessionId, attribute, blob);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	void ingestStringKey_SessionId_Postfix(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			setStringKey_Postfix(map, relevantMaps, sessionId, attribute, blob);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	void ingestStringKey_SessionId_Prefix(IMap map, IMap[] relevantMaps, byte[] blob) {
		for (int i = 0; i < count; i++) {
			String sessionId = UUID.randomUUID().toString();
			String attribute = "attr" + i;
			setStringKey_Prefix(map, relevantMaps, sessionId, attribute, blob);
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
		writeLine("   " + executableName
				+ " - Ingest mock data into the specified maps for testing the session expiration plugin, "
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
		writeLine("   -entry entry_type");
		writeLine("             Entry type. INGEST for ingesting data, PUT for entering a single entry,");
		writeLine("             RESET for resetting idle timeout for a single entry. PUT and RESET require");
		writeLine("             session ID and attribute. Use '-session' and '-attribute' to sepcify them.");
		writeLine("");
		writeLine("   -session session_ID");
		writeLine("             Session ID that is part of the composite key");
		writeLine("");
		writeLine("   -attribute attribute");
		writeLine("             Attribute that is part of the composite key");
		writeLine("");
		writeLine("   -postfix true|false");
		writeLine("             true to set session ID as postfix in building keys; false to set session ID as");
		writeLine("             prefix. This option applies to the STRING type only. Postfix allows data affinity by");
		writeLine("             session ID but the predicate execution latency can be very high. Prefix provides");
		writeLine("             session ID. Note that if postfix is enabled, then it must also be enabled in the");
		writeLine("             member configuration file by setting the following property.");
		writeLine("                hazelcast.addon.cluster.expiration.string-key.postfix.enabled");
		writeLine("             Default: false");
		writeLine("");
		writeLine("   -metadata true|false");
		writeLine("             If true SessionMetadata is used. Any other value is false. This option requires");
		writeLine("             the entry type of PUT, RESET, or GET. Default: false");
		writeLine();
		writeLine("EXAMPLES");
		writeLine("   # [1] INTERFACE: Ingest InterfaceKey that implements ISessionId into");
		writeLine("   #                smki_EN01, mki1_EN01, mki2_EN02");
		writeLine("   ./" + executableName + " -type INTERFACE -primary smki_EN01 -relevant mki1_EN01,mki2_EN01");
		writeLine();
		writeLine("   # [2] OBJECT: Ingest objects (ObjectKey) with the getSessionId() method into");
		writeLine("   #             smko_EN01, mko1_EN01, mko2_EN02");
		writeLine("   #     The key property, sessionId, is specified in the cluster config file.");
		writeLine("   ./test_session_ingestion -type OBJECT -primary smko_EN01 -relevant mko1_EN01,mko2_EN01");
		writeLine();
		writeLine("   #[3]  CUSTOM: Ingest CustomKey objects into");
		writeLine("   #             smkc_EN01, mkc_EN01, mkc2_EN02");
		writeLine("   #     SessionID is extracted by CustomPrediate specified in the cluster config file.");
		writeLine("   ./" + executableName + " -type CUSTOM -primary smkc_EN01 -relevant mkc1_EN01,mkc2_EN01");
		writeLine();
		writeLine("   # [4] PARTITION_AWARE: Ingest PartitionAwareKey that implements PartitionAware into");
		writeLine("   #                     smkp_EN01, mkp1_EN01, mkp2_EN02");
		writeLine("   #     It uses PartitionAware.getPartionKey() as the session ID.");
		writeLine("   ./test_session_ingestion -type PARTITION_AWARE -primary smkp_EN01 -relevant mkp1_EN01,mkp2_EN01");
		writeLine();
		writeLine("   # [5] STRING: Ingest String keys into");
		writeLine("   #             smks_EN01, mks_EN01, mks2_EN02");
		writeLine("   #     SessionID is extracted from key objects using the specified delimiter in the cluster");
		writeLine("   #     config file. The default delimiter is '@' and the session ID is the last part of the key.");
		writeLine("   ./" + executableName + " -type STRING -primary smks_EN01 -relevant mks1_EN01,mks2_EN01");
		writeLine();
		writeLine("   # [6] STRING: Ingest String keys into");
		writeLine("   #             smkn_EN01, mkn_EN01, mkn2_EN02");
		writeLine("   #     These maps have been configured with max idle timeout without the plugin.");
		writeLine("   #     Run this command to compare ingestion performance.");
		writeLine("   ./" + executableName + " -type STRING -primary smkn_EN01 -relevant mkn1_EN01,mkn2_EN01");
		writeLine();
		writeLine("   # [7] OBJECT: Ingest objects (ObjectKey) with the getSessionId() method into");
		writeLine("   #             mkp_session_web_session_fi_session_id_mapping_EN0,");
		writeLine("   #             mmkp_session_fi_session_data_EN01,");
		writeLine("   #             mkp_session_application_data_EN0");
		writeLine("   #     The key property, sessionId, is specified in the cluster config file.");
		writeLine(
				"   ./test_session_ingestion -type OBJECT -primary mkp_session_web_session_fi_session_id_mapping_EN01 -relevant mkp_session_fi_session_data_EN01,mkp_session_application_data_EN01");
		writeLine();
		writeLine("   # [8] INTERFACE PUT: Put a single entry to both primary and relevant maps.");
		writeLine("   ./" + executableName
				+ " -type INTERFACE -primary smki_EN01 -relevant mki1_EN01,mki2_EN01 -entry PUT -session s1 -attribute a1");
		writeLine();
		writeLine("   # [9] INTERFACE RESET: Reset a single entry by writing to the primary map.");
		writeLine("   ./" + executableName
				+ " -type INTERFACE -primary smki_EN01 -relevant mki1_EN01,mki2_EN01 -entry RESET -session s1 -attribute a1");
		writeLine();
		writeLine(
				"   # [10] INTERFACE GET: Get a single entry from the primary map. This call resets the primary map only.");
		writeLine("   #                   Relevant maps are not reset.");
		writeLine("   ./" + executableName
				+ " -type INTERFACE -primary smki_EN01 -relevant mki1_EN01,mki2_EN01 -entry GET -session s1 -attribute a1");
		writeLine();
		writeLine("   # [11] STRING METADATA PUT: Put a single entry to both primary and relevant maps.");
		writeLine("   ./" + executableName
				+ " -type STRING -primary smks_EN01 -relevant mks1_EN01,mks2_EN01 -entry PUT -session s1 -attribute a1 -metadata true");
		writeLine();
		writeLine("   # [12] STRING METADATA RESET: Reset a single entry by writing to the primary map.");
		writeLine("   ./" + executableName
				+ " -type STRING -primary smks_EN01 -relevant mks1_EN01,mks2_EN01 -entry RESET -session s1 -attribute a1 -metadata true");
		writeLine();
		writeLine(
				"   # [13] STRING METADATA GET: Get a single entry from the primary map. This call resets the primary map only.");
		writeLine("   #                Relevant maps are not reset.");
		writeLine("   ./" + executableName
				+ " -type INTERFACE -primary smki_EN01 -relevant mki1_EN01,mki2_EN01 -entry GET -session s1 -attribute a1 -metadata true");
		writeLine();
		writeLine("NOTES");
		writeLine("   - To test session metadata run [11] and repeatedly run [12].");
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
		String sessionId = null;
		String attribute = null;
		String entry = null;
		EntryType entryType = EntryType.INGEST;
		boolean isMetadata = false;
		boolean isPostfix = false;
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
			} else if (arg.startsWith("-entry")) {
				if (i < args.length - 1) {
					entry = args[++i].trim();
					if (entry.equalsIgnoreCase("put")) {
						entryType = EntryType.PUT;
					} else if (entry.equalsIgnoreCase("reset")) {
						entryType = EntryType.RESET;
					} else if (entry.equalsIgnoreCase("get")) {
						entryType = EntryType.GET;
					} else {
						System.err.println("ERROR: Invalid entry type [" + entry
								+ "]. Valid values are [put, reset, get]. Command aborted.");
						System.exit(1);
					}
				}
			} else if (arg.startsWith("-session")) {
				if (i < args.length - 1) {
					sessionId = args[++i].trim();
				}
			} else if (arg.startsWith("-attribute")) {
				if (i < args.length - 1) {
					attribute = args[++i].trim();
				}
			} else if (arg.startsWith("-postfix")) {
				if (i < args.length - 1) {
					String prefix = args[++i].trim();
					isPostfix = prefix.equalsIgnoreCase("true");
				}
			} else if (arg.startsWith("-metadata")) {
				if (i < args.length - 1) {
					String metadata = args[++i].trim();
					isMetadata = metadata.equalsIgnoreCase("true");
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

		switch (entryType) {
		case PUT:
		case RESET:
		case GET:
			if (sessionId == null) {
				System.err.println(
						"ERROR: Session ID not specified. Use '-session' to specify session ID. Command aborted.");
				System.exit(5);
			}
			if (attribute == null) {
				System.err.println(
						"ERROR: Attribute not specified. Use '-attribute' to specify attribute. Command aborted.");
				System.exit(5);
			}
			break;
		default:
			break;
		}

		String[] relevantMapNameArray = relevantMapNames.split(",");
		SessionExpirationTestClient client = new SessionExpirationTestClient(keyType, count, primaryMapName,
				relevantMapNameArray, entryType, sessionId, attribute);
		client.ingestData(isMetadata, isPostfix);
		client.shutdown();
	}

}
