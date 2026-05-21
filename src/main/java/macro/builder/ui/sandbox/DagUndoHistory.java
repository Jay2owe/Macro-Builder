package macro.builder.ui.sandbox;

import macro.builder.image.dag.DagIR;

import java.util.ArrayDeque;
import java.util.Deque;

final class DagUndoHistory {

    static final int DEFAULT_MAX_DEPTH = 50;

    private final int maxDepth;
    private final Deque<DagIR> previousStates = new ArrayDeque<DagIR>();
    private DagIR currentState;

    DagUndoHistory(DagIR initialState) {
        this(initialState, DEFAULT_MAX_DEPTH);
    }

    DagUndoHistory(DagIR initialState, int maxDepth) {
        this.maxDepth = Math.max(1, maxDepth);
        this.currentState = initialState;
    }

    boolean record(DagIR nextState) {
        if (nextState == null) return false;
        if (currentState == null) {
            currentState = nextState;
            return false;
        }
        if (nextState.equals(currentState)) return false;
        previousStates.addLast(currentState);
        while (previousStates.size() > maxDepth) {
            previousStates.removeFirst();
        }
        currentState = nextState;
        return true;
    }

    boolean canUndo() {
        return !previousStates.isEmpty();
    }

    DagIR undo() {
        if (previousStates.isEmpty()) return null;
        currentState = previousStates.removeLast();
        return currentState;
    }

    void reset(DagIR state) {
        previousStates.clear();
        currentState = state;
    }
}
