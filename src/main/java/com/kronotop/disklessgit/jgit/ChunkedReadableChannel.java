/*
 * Copyright (c) 2026 Burak Sezer
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kronotop.disklessgit.jgit;

import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

final class ChunkedReadableChannel implements ReadableChannel {
    private static final int CHUNK_SIZE = KronotopStore.CHUNK_SIZE;

    private final KronotopStore store;
    private final String file;
    private final long size;
    private long position;
    private boolean open = true;
    private int cachedSeq = -1;
    private byte[] cachedChunk;

    ChunkedReadableChannel(KronotopStore store, String file, long size) {
        this.store = store;
        this.file = file;
        this.size = size;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (position >= size) {
            return -1;
        }
        int seq = (int) (position / CHUNK_SIZE);
        if (seq != cachedSeq) {
            cachedChunk = store.readChunk(file, seq);
            cachedSeq = seq;
        }
        int offset = (int) (position % CHUNK_SIZE);
        int n = Math.min(dst.remaining(), cachedChunk.length - offset);
        if (n <= 0) {
            return -1;
        }
        dst.put(cachedChunk, offset, n);
        position += n;
        return n;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public void position(long newPosition) {
        position = newPosition;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public int blockSize() {
        return CHUNK_SIZE;
    }

    @Override
    public void setReadAheadBytes(int bufferSize) {
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
        cachedChunk = null;
    }
}
