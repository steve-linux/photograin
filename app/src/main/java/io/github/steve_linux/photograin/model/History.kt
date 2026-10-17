// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/** Unlimited undo/redo over immutable states. */
class History<T>(initial: T) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Records [state] as a new step. Does nothing if it equals the current state. */
    fun commit(state: T) {
        if (state == current) return
        undoStack.addLast(current)
        redoStack.clear()
        current = state
    }

    fun undo(): T {
        if (canUndo) {
            redoStack.addLast(current)
            current = undoStack.removeLast()
        }
        return current
    }

    fun redo(): T {
        if (canRedo) {
            undoStack.addLast(current)
            current = redoStack.removeLast()
        }
        return current
    }

    /** Starts over from [state], forgetting all steps. */
    fun reset(state: T) {
        undoStack.clear()
        redoStack.clear()
        current = state
    }
}
