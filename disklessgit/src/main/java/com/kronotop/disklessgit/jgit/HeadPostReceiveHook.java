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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public final class HeadPostReceiveHook implements PostReceiveHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeadPostReceiveHook.class);
    private static final List<String> PREFERRED = List.of(Constants.R_HEADS + "main", Constants.R_HEADS + Constants.MASTER);

    private static String selectBranch(Collection<ReceiveCommand> commands) {
        String first = null;
        for (ReceiveCommand command : commands) {
            if (command.getResult() != ReceiveCommand.Result.OK
                    || command.getType() == ReceiveCommand.Type.DELETE
                    || !command.getRefName().startsWith(Constants.R_HEADS)) {
                continue;
            }
            if (PREFERRED.contains(command.getRefName())) {
                return command.getRefName();
            }
            if (first == null) {
                first = command.getRefName();
            }
        }
        return first;
    }

    @Override
    public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        String branch = selectBranch(commands);
        if (branch == null) {
            return;
        }
        KronotopStore store = ((KronotopRepository) rp.getRepository()).store();
        try {
            store.linkHeadIfDangling(branch);
        } catch (IOException e) {
            LOGGER.warn("Cannot link HEAD to '{}' in '{}'", branch, store.namespace(), e);
        }
    }
}
