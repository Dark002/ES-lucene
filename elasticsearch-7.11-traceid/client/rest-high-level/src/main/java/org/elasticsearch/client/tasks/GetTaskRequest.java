/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.client.tasks;

import org.elasticsearch.client.Validatable;
import org.elasticsearch.client.ValidationException;
import org.elasticsearch.common.unit.TimeValue;

import java.util.Objects;
import java.util.Optional;

public class GetTaskRequest implements Validatable {
    private final String nodeId;
    private final long taskId;
    private boolean waitForCompletion = false;
    private TimeValue timeout = null;

    public GetTaskRequest(String nodeId, long taskId) {
        this.nodeId = nodeId;
        this.taskId = taskId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public long getTaskId() {
        return taskId;
    }

    /**
     * Should this request wait for all found tasks to complete?
     */
    public boolean getWaitForCompletion() {
        return waitForCompletion;
    }

    /**
     * Should this request wait for all found tasks to complete?
     */
    public GetTaskRequest setWaitForCompletion(boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
        return this;
    }

    /**
     * Timeout to wait for any async actions this request must take. It must take anywhere from 0 to 2.
     */
    public TimeValue getTimeout() {
        return timeout;
    }

    /**
     * Timeout to wait for any async actions this request must take.
     */
    public GetTaskRequest setTimeout(TimeValue timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public Optional<ValidationException> validate() {
        final ValidationException validationException = new ValidationException();
        if (timeout != null && !waitForCompletion) {
            validationException.addValidationError("Timeout settings are only accepted if waitForCompletion is also set");
        }
        if (validationException.validationErrors().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(validationException);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, taskId, waitForCompletion, timeout);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GetTaskRequest other = (GetTaskRequest) obj;
        return Objects.equals(nodeId, other.nodeId) &&
                taskId == other.taskId &&
                waitForCompletion == other.waitForCompletion &&
                Objects.equals(timeout, other.timeout);
    }
}
