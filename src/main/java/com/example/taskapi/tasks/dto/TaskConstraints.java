package com.example.taskapi.tasks.dto;

final class TaskConstraints {

    static final String TAG_PATTERN = "^[A-Za-z0-9][A-Za-z0-9 _-]*$";
    static final String TAG_MESSAGE = "tags may contain letters, digits, spaces, '-' and '_'";

    private TaskConstraints() {}
}
