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

import org.eclipse.jgit.internal.storage.dfs.DfsOutputStream;
import org.eclipse.jgit.internal.storage.dfs.DfsPackDescription;
import org.eclipse.jgit.internal.storage.pack.PackExt;

import java.io.IOException;
import java.nio.ByteBuffer;

final class ChunkedOutputStream extends DfsOutputStream {
    private static final int CHUNK_SIZE = KronotopStore.CHUNK_SIZE;

    private final KronotopStore store;
    private final DfsPackDescription desc;
    private final PackExt ext;
    private final String file;
    private final byte[] buffer = new byte[CHUNK_SIZE];
    private int bufferLength;
    private int seq;
    private boolean closed;

    ChunkedOutputStream(KronotopStore store, DfsPackDescription desc, PackExt ext) {
        this.store = store;
        this.desc = desc;
        this.ext = ext;
        this.file = desc.getFileName(ext);
    }

    @Override
    public int blockSize() {
        return CHUNK_SIZE;
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int n = Math.min(len, CHUNK_SIZE - bufferLength);
            System.arraycopy(buf, off, buffer, bufferLength, n);
            bufferLength += n;
            off += n;
            len -= n;
            if (bufferLength == CHUNK_SIZE) {
                store.insertChunk(file, seq++, buffer, bufferLength);
                bufferLength = 0;
            }
        }
    }

    @Override
    public int read(long position, ByteBuffer dst) throws IOException {
        long flushed = (long) seq * CHUNK_SIZE;
        if (position >= flushed) {
            int offset = (int) (position - flushed);
            int n = Math.min(dst.remaining(), bufferLength - offset);
            if (n <= 0) {
                return -1;
            }
            dst.put(buffer, offset, n);
            return n;
        }
        byte[] chunk = store.readChunk(file, (int) (position / CHUNK_SIZE));
        int offset = (int) (position % CHUNK_SIZE);
        int n = Math.min(dst.remaining(), chunk.length - offset);
        dst.put(chunk, offset, n);
        return n;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        long size = (long) seq * CHUNK_SIZE + bufferLength;
        if (bufferLength > 0) {
            store.insertChunk(file, seq++, buffer, bufferLength);
            bufferLength = 0;
        }
        desc.addFileExt(ext);
        desc.setFileSize(ext, size);
        desc.setBlockSize(ext, CHUNK_SIZE);
    }
}
