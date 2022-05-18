package org.hazelcast.addon.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.partition.Partition;
import com.hazelcast.query.PartitionPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;

/**
 * {@linkplain MapUtil} provides IMap specific utility methods.
 * 
 * @author dpark
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class MapUtil {

	private static int[] partitionIdsToKeys;

	/**
	 * Removes all keys found by applying the specified predicate on the local map
	 * key set.
	 * 
	 * @param map       Map from which the predicate is to be applied.
	 * @param predicate Predicate to execute on the local keys. To execute it on
	 *                  values, use
	 *                  {@link #removeMemberAll(HazelcastInstance, IMap, Predicate)}.
	 */
	public static void removeMemberAllKeySet(IMap map, Predicate predicate) {
		Set keySet = map.localKeySet(predicate);
		for (Object key : keySet) {
			map.remove(key);
		}
	}

	/**
	 * Removes all entries in the local member partitions that match the specified
	 * predicate. This is an expensive operation as it executes the predicate on each
	 * primary partition. If the predicate is for keys, then use
	 * {@link #removeMemberAllKeySet(IMap, Predicate)} instead for better
	 * performance.
	 * 
	 * @param hz        HazelcastInstance
	 * @param map       Map from which the predicate is to be applied.
	 * @param predicate Predicate to execute.
	 */
	public static void removeMemberAll(HazelcastInstance hz, IMap map, Predicate predicate) {
		Collection<Partition> localOwnerPartitions = getLocalOwnerPartitions(hz);
		int[] partitionIdsToKeys = getParitionIdsToKeys(hz);
		for (Partition partition : localOwnerPartitions) {
			PartitionPredicate<?, ?> partitionPredicate = Predicates
					.partitionPredicate(partitionIdsToKeys[partition.getPartitionId()], predicate);
			map.removeAll(partitionPredicate);
		}
	}

	/**
	 * Executes the specified predicate on only the partitions owned by the local
	 * member.
	 * 
	 * @param hz        HazelcastInstance.
	 * @param map       Map to execute the predicate on.
	 * @param predicate Predicate to execute.
	 * @return
	 */
	public static Collection queryMemberKeySet(IMap map, Predicate predicate) {
		if (map == null) {
			return null;
		}
		return map.localKeySet(predicate);
	}

	/**
	 * Executes the specified predicate on the partition that maps to the specified
	 * partition key.
	 * 
	 * @param map          Map to execute the predicate on.
	 * @param partitionKey Partition key
	 * @param predicate    Predicate to execute.
	 * @return
	 */
	public static Collection queryPartitionValues(IMap map, Object partitionKey, Predicate predicate) {
		List results = new ArrayList();
		PartitionPredicate<?, ?> partitionPredicate = Predicates.partitionPredicate(partitionKey, predicate);
		Collection col = map.values(partitionPredicate);
		results.addAll(col);
		return results;
	}

	/**
	 * Executes the specified predicate on the partition that maps to the specified
	 * partition ID.
	 * 
	 * @param hz          HazelcastInstance
	 * @param map         Map to execute the predicate on.
	 * @param partitionId Partition ID.
	 * @param predicate   Predicate to execute.
	 */
	public static Collection queryPartitionValues(HazelcastInstance hz, IMap map, int partitionId,
			Predicate predicate) {
		List results = new ArrayList();
		int[] partitionIdsToKeys = getParitionIdsToKeys(hz);
		PartitionPredicate<?, ?> partitionPredicate = Predicates.partitionPredicate(partitionIdsToKeys[partitionId],
				predicate);
		Collection col = map.values(partitionPredicate);
		results.addAll(col);
		return results;
	}

	/**
	 * Executes the specified predicate on only the partitions owned by the local
	 * member.
	 * 
	 * @param hz        HazelcastInstance
	 * @param map       Map to execute the predicate on.
	 * @param predicate Predicate to execute.
	 * @return Result set containing entry values.
	 */
	public static Collection queryMemberValues(HazelcastInstance hz, IMap map, Predicate predicate) {
		List results = new ArrayList();
		Collection<Partition> localOwnerPartitions = getLocalOwnerPartitions(hz);
		int[] partitionIdsToKeys = getParitionIdsToKeys(hz);
		for (Partition partition : localOwnerPartitions) {
			PartitionPredicate<?, ?> partitionPredicate = Predicates
					.partitionPredicate(partitionIdsToKeys[partition.getPartitionId()], predicate);
			Collection col = map.values(partitionPredicate);
			results.addAll(col);
		}
		return results;
	}

	/**
	 * Executes the specified predicate on only the partitions owned by the local
	 * member.
	 * 
	 * @param hz        HazelcastInstance
	 * @param map       Map to execute the predicate on.
	 * @param predicate Predicate to execute.
	 * @return Result set containing keys and values, i.e., Map.Entry.
	 */
	public static Collection queryMemberEntrySet(HazelcastInstance hz, IMap map, Predicate predicate) {
		List results = new ArrayList();
		Collection<Partition> localOwnerPartitions = getLocalOwnerPartitions(hz);
		int[] partitionIdsToKeys = getParitionIdsToKeys(hz);
		for (Partition partition : localOwnerPartitions) {
			PartitionPredicate<?, ?> partitionPredicate = Predicates
					.partitionPredicate(partitionIdsToKeys[partition.getPartitionId()], predicate);
			Collection col = map.entrySet(partitionPredicate);
			results.addAll(col);
		}
		return results;
	}

	/**
	 * Returns all the partitions owned by the local member.
	 * 
	 * @param hz HazelcastInstance
	 */
	public static Collection<Partition> getLocalOwnerPartitions(HazelcastInstance hz) {

		ArrayList<Partition> localOwnerPartitionSet = new ArrayList<Partition>();
		Member thisMember = hz.getCluster().getLocalMember();
		Set<Partition> partitionSet = hz.getPartitionService().getPartitions();
		for (Partition partition : partitionSet) {
			if (thisMember == partition.getOwner()) {
				localOwnerPartitionSet.add(partition);
			}
		}
		return localOwnerPartitionSet;
	}

	/**
	 * Returns an array containing partition keys. The array indexes represent
	 * partition IDs.
	 * 
	 * @param hz HazelcastInstance
	 */
	public static synchronized int[] getParitionIdsToKeys(HazelcastInstance hz) {
		if (partitionIdsToKeys == null) {
			int partitionCount = hz.getPartitionService().getPartitions().size();
			partitionIdsToKeys = new int[partitionCount];
			for (int i = 0; i < partitionCount; i++) {
				partitionIdsToKeys[i] = -1;
			}
			int count = 0;
			int key = 0;
			while (count < partitionCount) {
				Partition partition = hz.getPartitionService().getPartition(key);
				int id = partition.getPartitionId();
				if (partitionIdsToKeys[id] == -1) {
					partitionIdsToKeys[id] = key;
					count++;
				}
				key++;
			}
		}
		return partitionIdsToKeys;
	}

	/**
	 * Resets partitionIdsToKeys so that it must be recreated when next time it is
	 * invoked. This method must be invoked whenever partition rebalancing
	 * completes.
	 */
	public static synchronized void resetPartionIdsToKeys() {
		partitionIdsToKeys = null;
	}
}