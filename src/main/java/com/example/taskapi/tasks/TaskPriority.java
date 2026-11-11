package com.example.taskapi.tasks;

/** {@code rank} is persisted alongside the name so sorting by priority is LOW < MEDIUM < HIGH. */
public enum TaskPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int rank;

    TaskPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
