/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.contrib.streaming.state;

import org.apache.flink.util.IOUtils;
import org.apache.flink.util.Preconditions;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * It's a wrapper class around RocksDB's {@link WriteBatch} for writing in bulk.
 *
 * <p>IMPORTANT: This class is not thread safe.
 */
public class RocksDBWriteBatchWrapper implements AutoCloseable {

	private static final int MIN_CAPACITY = 10 * 1024;
	private static final int MAX_CAPACITY = 50 * 1024 * 1024;
	private static final int DEFAULT_CAPACITY = 5 * 1024 * 1024;
	private static final int INITIAL_SIZE = 50 * 1024;
	private static final int MAX_RECORDS = 1000;

	private final RocksDB db;

	private final WriteBatch batch;

	private final WriteOptions options;

	private final int capacity;

	public RocksDBWriteBatchWrapper(@Nonnull RocksDB rocksDB) {
		this(rocksDB, null, DEFAULT_CAPACITY);
	}

	public RocksDBWriteBatchWrapper(@Nonnull RocksDB rocksDB, @Nullable WriteOptions options) {
		this(rocksDB, options, DEFAULT_CAPACITY);
	}

	public RocksDBWriteBatchWrapper(@Nonnull RocksDB rocksDB, @Nullable WriteOptions options, int capacity) {
		Preconditions.checkArgument(capacity >= MIN_CAPACITY && capacity <= MAX_CAPACITY,
			"capacity should be between " + MIN_CAPACITY + " and " + MAX_CAPACITY);

		this.db = rocksDB;
		this.options = options;
		this.capacity = capacity;
		this.batch = new WriteBatch(INITIAL_SIZE);
	}

	public void put(
		@Nonnull ColumnFamilyHandle handle,
		@Nonnull byte[] key,
		@Nonnull byte[] value) throws RocksDBException {

		batch.put(handle, key, value);

		maybeFlush();
	}

	public void remove(
		@Nonnull ColumnFamilyHandle handle,
		@Nonnull byte[] key) throws RocksDBException {

		batch.delete(handle, key);

		maybeFlush();
	}

	private void maybeFlush() throws RocksDBException {
		if (batch.getDataSize() >= capacity || batch.count() >= MAX_RECORDS) {
			flush();
		}
	}

	public void flush() throws RocksDBException {
		if (options != null) {
			db.write(options, batch);
		} else {
			// use the default WriteOptions, if wasn't provided.
			try (WriteOptions writeOptions = new WriteOptions()) {
				db.write(writeOptions, batch);
			}
		}
		batch.clear();
	}

	public WriteOptions getOptions() {
		return options;
	}

	@Override
	public void close() throws RocksDBException {
		if (batch.count() != 0) {
			flush();
		}
		IOUtils.closeQuietly(batch);
	}
}
