package org.hazelcast.addon.cluster.expiration.test.junit;

import static org.junit.Assert.assertEquals;

import org.hazelcast.addon.cluster.expiration.SessionMapUtil;
import org.junit.Test;

public class SessionMapUtilTest {

	/**
	 * If tokens null then empty tag (no tag)
	 */
	@Test
	public void testSessionMapUtilTokensNull() {
		String sessionMapName = "abc_xyz";

		String[] tokens = null;
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals("", tag);
	}
	
	/**
	 * If tokens array size is 0 then empty tag (no tag)
	 */
	@Test
	public void testSessionMapUtilTokensEmpty() {
		String key = "abc_notag_xyz";
		String sessionMapName = "abc_xyz_EN01";

		String[] tokens = null;
		if (key.indexOf("%TAG%") == -1) {
			tokens = new String[0];
		}
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals("", tag);
	}
	
	/**
	 * If tag is at the end of the string value then valid
	 */
	@Test
	public void testSessionMapUtilTagEnd() {
		String key = "abc_xyz_%TAG%";
		String sessionMapName = "abc_xyz_EN01";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals("EN01", tag);
	}

	/**
	 * If %TAG% value placement mismatch then null
	 */
	@Test
	public void testSessionMapUtilNull() {
		String key = "abc_%TAG%_xyz";
		String sessionMapName = "abc_xyz_EN01";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals(null, tag);
	}

	/**
	 * If %TAG% value is not specified, then null
	 */
	@Test
	public void testSessionMapUtilNoTagNull() {
		String key = "abc_%TAG%_xyz";
		String sessionMapName = "abc__xyz_EN01";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals(null, tag);
	}

	/**
	 * If single %TAG% value match then valid tag
	 */
	@Test
	public void testSessionMapUtilEN01() {
		String key = "abc_%TAG%_xyz";
		String sessionMapName = "abc_EN01_xyz";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals("EN01", tag);
	}

	/**
	 * If %TAG% value match but tokens do not match then null
	 */
	@Test
	public void testSessionMapUtilEN01_2() {
		String key = "abc_%TAG%_xyz";
		String sessionMapName = "abc_EN01_xyz_test";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals(null, tag);
	}

	/**
	 * If multiple %TAG% values match then valid tag
	 */
	@Test
	public void testSessionMapUtilEN01_3() {
		String key = "abc_%TAG%_xyz_%TAG%_test";
		String sessionMapName = "abc_EN01_xyz_EN01_test";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals("EN01", tag);
	}

	/**
	 * If multiple %TAG% values mismatch then null
	 */
	@Test
	public void testSessionMapUtilEN01_4() {
		String key = "abc_%TAG%_xyz_%TAG%_test";
		String sessionMapName = "abc_EN01_xyz_EN02_test";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals(null, tag);
	}

	/**
	 * If multiple %TAG% values match but tokens mismatch then null
	 */
	@Test
	public void testSessionMapUtilEN01_5() {
		String key = "abc_%TAG%_xyz_%TAG%_test";
		String sessionMapName = "abc_EN01_xyz_EN01_test2";

		String[] tokens = key.split("%TAG%");
		String tag = SessionMapUtil.getTag(sessionMapName, tokens);

		assertEquals(null, tag);
	}
}
