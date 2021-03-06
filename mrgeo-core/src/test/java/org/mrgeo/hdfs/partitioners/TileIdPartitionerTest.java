/*
 * Copyright 2009-2014 DigitalGlobe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mrgeo.hdfs.partitioners;

import org.apache.hadoop.conf.Configuration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mrgeo.core.Defs;
import org.mrgeo.data.DataProviderFactory;
import org.mrgeo.data.DataProviderFactory.AccessMode;
import org.mrgeo.data.KVIterator;
import org.mrgeo.data.image.MrsImageDataProvider;
import org.mrgeo.data.tile.MrsTileReader;
import org.mrgeo.data.tile.TileIdWritable;
import org.mrgeo.junit.UnitTest;
import org.mrgeo.utils.HadoopUtils;

import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Properties;

@SuppressWarnings("static-method")
public class TileIdPartitionerTest {
	// this one has four partitions
	String allOnesImage = Defs.CWD + "/" + Defs.INPUT + "all-ones/10";
	
	// this one has only one partition
	String smallElevationImage = Defs.CWD + "/" + Defs.INPUT + "small-elevation";
	
	MrsTileReader<Raster> reader;

	@Before
	public void setUp() throws IOException
	{
	  MrsImageDataProvider provider = DataProviderFactory.getMrsImageDataProvider(smallElevationImage,
	      AccessMode.READ, (Properties)null);
		reader = provider.getMrsTileReader(10);
	}

	@After
	public void tearDown()
	{
		reader.close();
	}

	@Test
	@Category(UnitTest.class)
	public void testNonExistentSplitFile() throws URISyntaxException
	{
		File file = new File(new java.net.URI(smallElevationImage));
		boolean imageExists = file.exists();
		Assert.assertEquals(true, imageExists);

		// Make sure neither splits file exists in the source...
		String splitPath = "file://" + file.getAbsolutePath() + "/splits.txt";
		File splitFile = new File(splitPath);
		boolean splitExists = splitFile.exists();
		Assert.assertEquals(false, splitExists);

    splitPath = "file://" + file.getAbsolutePath() + "/splits";
    splitFile = new File(splitPath);
    splitExists = splitFile.exists();
    Assert.assertEquals(false, splitExists);

		int numEntries = read(Long.MIN_VALUE, Long.MAX_VALUE);
		Assert.assertEquals(numEntries,12);		  
	}
	
	@Test
	@Category(UnitTest.class)
	public void testFindPartition() {
		// tests to see if we have two partitions - 10 and 20, then keys 1, 10, 11, 20, 21 are 
		// assigned to partitions 0, 0, 1, 1, and 2
		// this covers all cases of keys on the split points, between split points, etc. 
		
		// start with two partitions
		TileIdWritable[] array = new TileIdWritable[]{new TileIdWritable(10),new TileIdWritable(20)};
		
		// left extreme is assigned to 0
		Assert.assertEquals(0, findPartition(new TileIdWritable(1),array));
		
		// on the first split point is assigned to 0
		Assert.assertEquals(0, findPartition(new TileIdWritable(10),array));
		
		// between two split points is assigned to 1
		Assert.assertEquals(1, findPartition(new TileIdWritable(11),array));
		
		// on the second split point is assigned to 1
		Assert.assertEquals(1, findPartition(new TileIdWritable(20),array));
		
		// right extreme is assigned to 2 
		Assert.assertEquals(2, findPartition(new TileIdWritable(21),array));	
	}
	
	@Test
	@Category(UnitTest.class)
	public void testSetSplitFileWithoutScheme() throws URISyntaxException {
		// no file:// scheme in the path
	  java.net.URI u = new java.net.URI(allOnesImage + "/splits");
		String splitFilePath = u.getPath();
		final int numSplits = 4;

		TileIdPartitioner<?, ?> partitioner = new TileIdPartitioner<Object, Object>();
		Configuration conf = HadoopUtils.createConfiguration();
		partitioner.setConf(conf);
		TileIdPartitioner.setSplitFile(splitFilePath, conf);
		
		TileIdWritable key = new TileIdWritable();
		
		// without scheme, getPartition will end up defaulting to empty splitPointArray, which
		// would always result in the partition=0, obviously incorrectly
		key.set(210835);
		Assert.assertEquals(0, partitioner.getPartition(key, null, numSplits));
	}
	@Test
	@Category(UnitTest.class)
	public void testSetSplitFileWithScheme() throws URISyntaxException {
		// has three splits, so four part directories
		final int numSplits = 3;
		String splitFilePath = allOnesImage + "/splits";
		java.net.URI u = new java.net.URI(splitFilePath);
		File splitFile = new File(u);
		boolean splitFileExists = splitFile.exists();
		Assert.assertEquals(true, splitFileExists);
		
		final String splitFilePathWithScheme = "file://" + splitFile.getAbsolutePath();
		
		TileIdPartitioner<?, ?> partitioner = new TileIdPartitioner<Object, Object>();
		Configuration conf = HadoopUtils.createConfiguration();
		partitioner.setConf(conf);
		TileIdPartitioner.setSplitFile(splitFilePathWithScheme, conf);
		
		TileIdWritable key = new TileIdWritable();
		
		key.set(208787);
		Assert.assertEquals(0, partitioner.getPartition(key, null, numSplits));
		
		key.set(208789);
		Assert.assertEquals(0, partitioner.getPartition(key, null, numSplits));

		key.set(209811);
		Assert.assertEquals(1, partitioner.getPartition(key, null, numSplits));

		key.set(210835);
		Assert.assertEquals(2, partitioner.getPartition(key, null, numSplits));

		key.set(211859);
		Assert.assertEquals(3, partitioner.getPartition(key, null, numSplits));

		// beyond image, but still in the last partition
		key.set(211862);
		Assert.assertEquals(3, partitioner.getPartition(key, null, numSplits));

	}
	
	private static int findPartition(TileIdWritable key, TileIdWritable[] array) {
		// find the bin for the range, and guarantee it is positive
		int index = Arrays.binarySearch(array, key);
		index = index < 0 ? (index + 1) * -1 : index;

		return index;
	}
	
	private int read(long start, long end) {
		TileIdWritable startKey = new TileIdWritable(start);
		TileIdWritable endKey = new TileIdWritable(end);
		int numEntries = 0;
		
    KVIterator<TileIdWritable, Raster> iter = reader.get(startKey, endKey);
    
    while (iter.hasNext())
    {
      numEntries++;
      iter.next();
    }

		return numEntries;
	}
}
