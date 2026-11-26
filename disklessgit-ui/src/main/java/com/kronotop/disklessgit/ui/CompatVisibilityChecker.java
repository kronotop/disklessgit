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

package com.kronotop.disklessgit.ui;

import com.google.gitiles.VisibilityChecker;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class CompatVisibilityChecker extends VisibilityChecker {
    private static RevCommit parseCommitOrNull(RevWalk walk, ObjectId id) throws IOException {
        try {
            return walk.parseCommit(id);
        } catch (IncorrectObjectTypeException e) {
            return null;
        }
    }

    @Override
    protected boolean isReachableFrom(String description, RevWalk walk, RevCommit commit, Collection<ObjectId> starters)
            throws IOException {
        List<RevCommit> startCommits = new ArrayList<>(starters.size());
        for (ObjectId id : starters) {
            RevCommit start = parseCommitOrNull(walk, id);
            if (start != null) {
                startCommits.add(start);
            }
        }
        if (startCommits.isEmpty()) {
            return false;
        }
        ReachabilityChecker checker = walk.getObjectReader().createReachabilityChecker(walk);
        return checker.areAllReachable(List.of(commit), startCommits.stream()).isEmpty();
    }
}
