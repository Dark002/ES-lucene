/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.core.ilm;

import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.xpack.core.ccr.action.PauseFollowAction;
import org.mockito.Mockito;

import java.util.Collections;

import static org.elasticsearch.xpack.core.ilm.UnfollowAction.CCR_METADATA_KEY;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

public class PauseFollowerIndexStepTests extends AbstractUnfollowIndexStepTestCase<PauseFollowerIndexStep> {

    @Override
    protected PauseFollowerIndexStep newInstance(Step.StepKey key, Step.StepKey nextKey) {
        return new PauseFollowerIndexStep(key, nextKey, client);
    }

    public void testPauseFollowingIndex() {
        IndexMetadata indexMetadata = IndexMetadata.builder("follower-index")
            .settings(settings(Version.CURRENT).put(LifecycleSettings.LIFECYCLE_INDEXING_COMPLETE, "true"))
            .putCustom(CCR_METADATA_KEY, Collections.emptyMap())
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();


        Mockito.doAnswer(invocation -> {
            PauseFollowAction.Request request = (PauseFollowAction.Request) invocation.getArguments()[1];
            assertThat(request.getFollowIndex(), equalTo("follower-index"));
            @SuppressWarnings("unchecked")
            ActionListener<AcknowledgedResponse> listener = (ActionListener<AcknowledgedResponse>) invocation.getArguments()[2];
            listener.onResponse(AcknowledgedResponse.TRUE);
            return null;
        }).when(client).execute(Mockito.same(PauseFollowAction.INSTANCE), Mockito.any(), Mockito.any());

        Boolean[] completed = new Boolean[1];
        Exception[] failure = new Exception[1];
        PauseFollowerIndexStep step = new PauseFollowerIndexStep(randomStepKey(), randomStepKey(), client);
        step.performAction(indexMetadata, null, null, new AsyncActionStep.Listener() {
            @Override
            public void onResponse(boolean complete) {
                completed[0] = complete;
            }

            @Override
            public void onFailure(Exception e) {
                failure[0] = e;
            }
        });
        assertThat(completed[0], is(true));
        assertThat(failure[0], nullValue());
    }

    public void testPauseFollowingIndexFailed() {
        IndexMetadata indexMetadata = IndexMetadata.builder("follower-index")
            .settings(settings(Version.CURRENT).put(LifecycleSettings.LIFECYCLE_INDEXING_COMPLETE, "true"))
            .putCustom(CCR_METADATA_KEY, Collections.emptyMap())
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();

        // Mock pause follow api call:
        Exception error = new RuntimeException();
        Mockito.doAnswer(invocation -> {
            PauseFollowAction.Request request = (PauseFollowAction.Request) invocation.getArguments()[1];
            assertThat(request.getFollowIndex(), equalTo("follower-index"));
            ActionListener listener = (ActionListener) invocation.getArguments()[2];
            listener.onFailure(error);
            return null;
        }).when(client).execute(Mockito.same(PauseFollowAction.INSTANCE), Mockito.any(), Mockito.any());

        Boolean[] completed = new Boolean[1];
        Exception[] failure = new Exception[1];
        PauseFollowerIndexStep step = new PauseFollowerIndexStep(randomStepKey(), randomStepKey(), client);
        step.performAction(indexMetadata, null, null, new AsyncActionStep.Listener() {
            @Override
            public void onResponse(boolean complete) {
                completed[0] = complete;
            }

            @Override
            public void onFailure(Exception e) {
                failure[0] = e;
            }
        });
        assertThat(completed[0], nullValue());
        assertThat(failure[0], sameInstance(error));
        Mockito.verify(client).execute(Mockito.same(PauseFollowAction.INSTANCE), Mockito.any(), Mockito.any());
        Mockito.verifyNoMoreInteractions(client);
    }
}
