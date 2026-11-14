/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.apache.ignite;

import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

/**
 * Leaves {@code // TODO Ignite 3: ...} comments on the line above the statement that holds the code they are about,
 * once each, so that several of them on one statement read as a list rather than as comments inside an expression.
 */
final class TodoComments {

    private static final String TODOS = "IGNITE_3_TODOS";

    private TodoComments() {
    }

    /**
     * Note a TODO for the statement that holds the tree at this cursor. The block holding that statement adds it
     * when {@link #addNoted(J.Block, Cursor)} is called from {@code visitBlock}.
     */
    static void noteOnEnclosingStatement(Cursor cursor, String todo) {
        for (Cursor c = cursor; c != null && !(c.getValue() instanceof SourceFile); c = c.getParent()) {
            if (c.getValue() instanceof Statement) {
                Cursor parent = c.getParentTreeCursor();
                if (parent.getValue() instanceof J.Block) {
                    Map<UUID, Set<String>> todos = parent.computeMessageIfAbsent(TODOS, k -> new HashMap<>());
                    todos.computeIfAbsent(((J) c.getValue()).getId(), k -> new LinkedHashSet<>()).add(todo);
                    return;
                }
            }
        }
    }

    /**
     * Add the TODOs noted for statements of this block.
     */
    static J.Block addNoted(J.Block block, Cursor blockCursor) {
        Map<UUID, Set<String>> todos = blockCursor.pollMessage(TODOS);
        if (todos == null) {
            return block;
        }
        return block.withStatements(ListUtils.map(block.getStatements(), s -> {
            Set<String> forStatement = todos.get(s.getId());
            return forStatement == null ? s : withTodos(s, forStatement);
        }));
    }

    static <J2 extends J> J2 withTodos(J2 j, Collection<String> todos) {
        Space prefix = j.getPrefix();
        List<Comment> comments = new ArrayList<>(prefix.getComments());
        String beforeElement = comments.isEmpty() ? prefix.getWhitespace() : comments.get(comments.size() - 1).getSuffix();
        boolean ownLine = beforeElement.contains("\n");
        String indent = beforeElement.substring(beforeElement.lastIndexOf('\n') + 1);
        boolean changed = false;
        for (String todo : todos) {
            if (hasTodo(comments, todo)) {
                continue;
            }
            comments.add(ownLine ?
                    new TextComment(false, " " + todo, "\n" + indent, Markers.EMPTY) :
                    new TextComment(true, " " + todo + " ", " ", Markers.EMPTY));
            changed = true;
        }
        if (!changed) {
            return j;
        }
        if (!ownLine && prefix.getComments().isEmpty() && !prefix.getWhitespace().isEmpty()) {
            // Keep the space that separated the element from what came before it, before the comment
            return j.withPrefix(Space.build(prefix.getWhitespace(), comments));
        }
        return j.withPrefix(prefix.withComments(comments));
    }

    private static boolean hasTodo(List<Comment> comments, String todo) {
        for (Comment comment : comments) {
            if (comment instanceof TextComment && ((TextComment) comment).getText().trim().equals(todo)) {
                return true;
            }
        }
        return false;
    }
}
