/*
 * Copyright 2015 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.linkedin.playparseq.trace.j;

import akka.Done$;
import akka.stream.Materializer;
import com.linkedin.playparseq.j.PlayParSeqImplTest;
import com.linkedin.playparseq.j.stores.ParSeqTaskStore;
import com.linkedin.playparseq.trace.j.renderers.ParSeqTraceRenderer;
import com.linkedin.playparseq.trace.j.sensors.ParSeqTraceSensor;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static play.mvc.Results.notFound;
import static play.mvc.Results.ok;
import static play.test.Helpers.*;


/**
 * The class ParSeqTraceBuilderImplTest is a test class for {@link ParSeqTraceBuilderImpl}.
 *
 * @author Yinan Ding (yding@linkedin.com)
 */
public class ParSeqTraceBuilderImplTest {

  /**
   * The method canBuildTrace tests the ability of building ParSeq Trace.
   */
  @Test
  public void canBuildTrace() {
    String render = "render";
    // Mock ParSeqTaskStore
    ParSeqTaskStore mockStore = mock(ParSeqTaskStore.class);
    when(mockStore.get(any(Http.Context.class))).thenReturn(new HashSet<>());
    // Mock ParSeqTraceSensor
    ParSeqTraceSensor mockTraceSensor = mock(ParSeqTraceSensor.class);
    when(mockTraceSensor.isEnabled(any(Http.Context.class), any(ParSeqTaskStore.class))).thenReturn(true);
    // Mock ParSeqTraceRenderer
    ParSeqTraceRenderer mockTraceRenderer = mock(ParSeqTraceRenderer.class);
    when(mockTraceRenderer.render(any(Http.Context.class), any(ParSeqTaskStore.class))).thenReturn(
        CompletableFuture.completedFuture(ok(render)));
    // Mock Materializer
    Materializer mockMaterializer = mock(Materializer.class);
    when(mockMaterializer.<CompletionStage<Done$>>materialize(any())).thenReturn(
        CompletableFuture.completedFuture(Done$.MODULE$));
    // Build ParSeq Trace
    ParSeqTraceBuilderImpl playParSeqTraceImpl = new ParSeqTraceBuilderImpl(mockMaterializer);
    Result result = PlayParSeqImplTest.getResultUnchecked(
        playParSeqTraceImpl.build(mock(Http.Context.class), CompletableFuture.completedFuture(notFound("origin")),
            mockStore, mockTraceSensor, mockTraceRenderer));
    // Assert the status and the content
    assertEquals(OK, result.status());
    assertEquals("text/plain", result.contentType().orElse(""));
    assertEquals(render, contentAsString(result));
  }

  /**
   * The method canShowOriginWhenTraceDisabled tests the ability of returning origin when the ParSeq Trace is disabled.
   */
  @Test
  public void canShowOriginWhenTraceDisabled() {
    String origin = "origin";
    // Mock ParSeqTaskStore
    ParSeqTaskStore mockStore = mock(ParSeqTaskStore.class);
    when(mockStore.get(any(Http.Context.class))).thenReturn(new HashSet<>());
    // Mock ParSeqTraceSensor
    ParSeqTraceSensor mockTraceSensor = mock(ParSeqTraceSensor.class);
    when(mockTraceSensor.isEnabled(any(Http.Context.class), any(ParSeqTaskStore.class))).thenReturn(false);
    // Mock ParSeqTraceRenderer
    ParSeqTraceRenderer mockTraceRenderer = mock(ParSeqTraceRenderer.class);
    when(mockTraceRenderer.render(any(Http.Context.class), any(ParSeqTaskStore.class))).thenReturn(
        CompletableFuture.completedFuture(ok("render")));
    // Mock Materializer
    Materializer mockMaterializer = mock(Materializer.class);
    when(mockMaterializer.<CompletionStage<Done$>>materialize(any())).thenReturn(
        CompletableFuture.completedFuture(Done$.MODULE$));
    // Build ParSeq Trace
    ParSeqTraceBuilderImpl playParSeqTraceImpl = new ParSeqTraceBuilderImpl(mockMaterializer);
    Result result = PlayParSeqImplTest.getResultUnchecked(
        playParSeqTraceImpl.build(mock(Http.Context.class), CompletableFuture.completedFuture(notFound(origin)),
            mockStore, mockTraceSensor, mockTraceRenderer));
    // Assert the status and the content
    assertEquals(NOT_FOUND, result.status());
    assertEquals("text/plain", result.contentType().orElse(""));
    assertEquals(origin, contentAsString(result));
  }

}
