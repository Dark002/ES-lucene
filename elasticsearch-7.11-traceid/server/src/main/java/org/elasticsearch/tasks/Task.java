/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */


package org.elasticsearch.tasks;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.ToXContentObject;

import java.io.IOException;
import java.util.Map;

/**
 * Current task information
 */
public class Task {

    /**
     * The request header to mark tasks with specific ids
     */
    public static final String X_OPAQUE_ID = "X-Opaque-Id";

    private final long id;

    private final String type;

    private final String action;

    private final String description;

    private final TaskId parentTask;

    private final Map<String, String> headers;

    /**
     * The task's start time as a wall clock time since epoch ({@link System#currentTimeMillis()} style).
     */
    private final long startTime;

    /**
     * The task's start time as a relative time ({@link System#nanoTime()} style).
     */
    private final long startTimeNanos;

    protected String distributedTraceId;

    public void setDistributedTraceId(String distributedTraceId) {
        this.distributedTraceId = distributedTraceId;
    }

    public Task(long id, String type, String action, String description, TaskId parentTask, Map<String, String> headers) {
        this(id, type, action, description, parentTask, System.currentTimeMillis(), System.nanoTime(), null, headers);
    }

    public Task(long id, String type, String action, String description, TaskId parentTask, String distributedTraceId, Map<String, String> headers) {
        this(id, type, action, description, parentTask, System.currentTimeMillis(), System.nanoTime(), distributedTraceId, headers);
    }

    public Task(long id, String type, String action, String description, TaskId parentTask, long startTime, long startTimeNanos,
        String distributedTraceId, Map<String, String> headers) {
        this.id = id;
        this.type = type;
        this.action = action;
        this.description = description;
        this.parentTask = parentTask;
        this.startTime = startTime;
        this.startTimeNanos = startTimeNanos;
        this.headers = headers;
        this.distributedTraceId = distributedTraceId;
    }

    /**
     * Build a version of the task status you can throw over the wire and back
     * to the user.
     *
     * @param localNodeId
     *            the id of the node this task is running on
     * @param detailed
     *            should the information include detailed, potentially slow to
     *            generate data?
     */
    public final TaskInfo taskInfo(String localNodeId, boolean detailed) {
        String description = null;
        Task.Status status = null;
        if (detailed) {
            description = getDescription();
            status = getStatus();
        }
        return taskInfo(localNodeId, description, status, distributedTraceId);
    }

    /**
     * Build a proper {@link TaskInfo} for this task.
     */
    protected final TaskInfo taskInfo(String localNodeId, String description, Status status, String distributedTraceId) {
        return new TaskInfo(new TaskId(localNodeId, getId()), getType(), getAction(), description, status, startTime,
                System.nanoTime() - startTimeNanos, this instanceof CancellableTask, parentTask, distributedTraceId, headers);
    }

    /**
     * Returns task id
     */
    public long getId() {
        return id;
    }

    /**
     * Returns task channel type (netty, transport, direct)
     */
    public String getType() {
        return type;
    }

    /**
     * Returns task action
     */
    public String getAction() {
        return action;
    }

    /**
     * Generates task description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the task's start time as a wall clock time since epoch ({@link System#currentTimeMillis()} style).
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns the task's start time in nanoseconds ({@link System#nanoTime()} style).
     */
    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    /**
     * Returns id of the parent task or NO_PARENT_ID if the task doesn't have any parent tasks
     */
    public TaskId getParentTaskId() {
        return parentTask;
    }

    /**
     * Build a status for this task or null if this task doesn't have status.
     * Since most tasks don't have status this defaults to returning null. While
     * this can never perform IO it might be a costly operation, requiring
     * collating lists of results, etc. So only use it if you need the value.
     */
    public Status getStatus() {
        return null;
    }

    /**
     * Report of the internal status of a task. These can vary wildly from task
     * to task because each task is implemented differently but we should try
     * to keep each task consistent from version to version where possible.
     * That means each implementation of {@linkplain Task.Status#toXContent}
     * should avoid making backwards incompatible changes to the rendered
     * result. But if we change the way a request is implemented it might not
     * be possible to preserve backwards compatibility. In that case, we
     * <b>can</b> change this on version upgrade but we should be careful
     * because some statuses (reindex) have become defacto standardized because
     * they are used by systems like Kibana.
     */
    public interface Status extends ToXContentObject, NamedWriteable {}

    /**
     * Returns stored task header associated with the task
     */
    public String getHeader(String header) {
        return headers.get(header);
    }

    public Map<String, String> headers() {
        return headers;
    }

    public TaskResult result(DiscoveryNode node, Exception error) throws IOException {
        return new TaskResult(taskInfo(node.getId(), true), error);
    }

    public TaskResult result(DiscoveryNode node, ActionResponse response) throws IOException {
        if (response instanceof ToXContent) {
            return new TaskResult(taskInfo(node.getId(), true), (ToXContent) response);
        } else {
            throw new IllegalStateException("response has to implement ToXContent to be able to store the results");
        }
    }
}
