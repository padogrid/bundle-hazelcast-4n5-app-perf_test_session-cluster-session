package org.hazelcast.addon.cluster.expiration;

public class SessionMapUtil {

	/**
	 * Returns the matching &lt;tag&gt;  value extracted from the specified map name.
	 * 
	 * @param mapName Map name
	 * @param tokens  Sequential parts of mapName split by the &lt;tag&gt;  value.
	 * @return Matching &lt;tag&gt;  value. Returns null if not found. Returns an empty
	 *         string if the specified tokens array is null or has the length of 0.
	 */
	public static String getTag(String mapName, String[] tokens) {
		String tag = null;
		if (tokens == null || tokens.length == 0) {
			tag = "";
		} else if (mapName.startsWith(tokens[0])) {
			int startIndex = tokens[0].length();
			String part2 = mapName.substring(startIndex);
			if (tokens.length > 1) {
				int index = part2.indexOf(tokens[1]);
				if (index == 0) {
					tag = null;
				} else if (index >= 0) {
					tag = part2.substring(0, index);
					String[] tokens2 = mapName.split(tag);
					if (tokens.length != tokens2.length) {
						tag = null;
					} else {
						for (int i = 0; i < tokens.length; i++) {
							if (tokens[i].equals(tokens2[i]) == false) {
								tag = null;
								break;
							}
						}
					}
				}
			} else {
				tag = part2;
			}
		}
		return tag;
	}

	/**
	 * Extracts and returns the matching &lt;tag&gt; value from the specified map name.
	 * 
	 * @param mapName       Map name
	 * @param taggedMapName Map name annotated with &lt;tag&gt; . Multiple &lt;tag&gt; 
	 *                      annotations allowed in a single tagged map name.
	 * @return null if the &lt;tag&gt; value not found in the specified map name, empty
	 *         string if &lt;tag&gt;  is not specified in taggedMapName, valid tag value if
	 *         found.
	 */
	public static String getTag(String mapName, String taggedMapName) {
		String tag = null;
		if (taggedMapName != null) {
			if (taggedMapName.indexOf(SessionExpirationServiceConfiguration.NAME_TAG) == -1) {
				tag = "";
			} else {
				String[] tokens = taggedMapName.split(SessionExpirationServiceConfiguration.NAME_TAG);
				tag = getTag(mapName, tokens);
			}
		}
		return tag;
	}
}
