package com.example.taskapi.tasks.dto;

import com.example.taskapi.tasks.TaskPriority;
import com.example.taskapi.tasks.TaskStatus;

public record TaskFilter(TaskStatus status, TaskPriority priority, String tag, String q) {}
