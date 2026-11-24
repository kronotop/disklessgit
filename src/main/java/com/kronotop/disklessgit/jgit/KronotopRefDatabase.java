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

import org.bson.BsonDocument;
import org.eclipse.jgit.internal.storage.dfs.DfsRefDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.util.RefList;

import java.io.IOException;

final class KronotopRefDatabase extends DfsRefDatabase {
    private final KronotopStore store;

    KronotopRefDatabase(KronotopRepository repository, KronotopStore store) {
        super(repository);
        this.store = store;
    }

    private static Ref toRef(BsonDocument document) {
        String name = document.getString("name").getValue();
        String target = document.getString("target").getValue();
        if (document.getBoolean("symbolic").getValue()) {
            return new SymbolicRef(name, new ObjectIdRef.Unpeeled(Ref.Storage.NEW, target, null));
        }
        return new ObjectIdRef.Unpeeled(Ref.Storage.PACKED, name, ObjectId.fromString(target));
    }

    private static BsonDocument toDocument(Ref ref) {
        if (ref.isSymbolic()) {
            return KronotopStore.refDocument(ref.getName(), ref.getTarget().getName(), true);
        }
        return KronotopStore.refDocument(ref.getName(), ref.getObjectId().name(), false);
    }

    private static boolean matches(Ref expected, BsonDocument current) {
        if (current == null) {
            return !expected.isSymbolic() && expected.getObjectId() == null;
        }
        boolean symbolic = current.getBoolean("symbolic").getValue();
        String target = current.getString("target").getValue();
        if (expected.isSymbolic()) {
            return symbolic && target.equals(expected.getTarget().getName());
        }
        return !symbolic && expected.getObjectId() != null && target.equals(expected.getObjectId().name());
    }

    private static boolean isPeelCache(Ref oldRef, Ref newRef) {
        return !oldRef.isSymbolic() && !newRef.isSymbolic()
                && oldRef.getObjectId() != null
                && oldRef.getObjectId().equals(newRef.getObjectId());
    }

    @Override
    protected RefCache scanAllRefs() throws IOException {
        RefList.Builder<Ref> ids = new RefList.Builder<>();
        RefList.Builder<Ref> sym = new RefList.Builder<>();
        for (BsonDocument document : store.readAllRefs()) {
            Ref ref = toRef(document);
            ids.add(ref);
            if (ref.isSymbolic()) {
                sym.add(ref);
            }
        }
        ids.sort();
        sym.sort();
        return new RefCache(ids.toRefList(), sym.toRefList());
    }

    @Override
    protected boolean compareAndPut(Ref oldRef, Ref newRef) throws IOException {
        if (isPeelCache(oldRef, newRef)) {
            return true;
        }
        boolean stored = store.compareAndSetRef(newRef.getName(), current -> matches(oldRef, current), toDocument(newRef));
        if (!stored) {
            refresh();
        }
        return stored;
    }

    @Override
    protected boolean compareAndRemove(Ref oldRef) throws IOException {
        boolean removed = store.compareAndSetRef(oldRef.getName(), current -> matches(oldRef, current), null);
        if (!removed) {
            refresh();
        }
        return removed;
    }

    @Override
    public ReflogReader getReflogReader(Ref ref) {
        return null;
    }
}
