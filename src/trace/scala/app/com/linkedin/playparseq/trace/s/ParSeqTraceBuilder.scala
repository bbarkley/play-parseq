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
package com.linkedin.playparseq.trace.s

import akka.stream.Materializer
import com.linkedin.playparseq.s.stores.ParSeqTaskStore
import com.linkedin.playparseq.trace.s.renderers.ParSeqTraceRenderer
import com.linkedin.playparseq.trace.s.sensors.ParSeqTraceSensor
import com.linkedin.playparseq.trace.utils.PlayParSeqTraceHelper
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}


/**
 * The trait ParSeqTraceBuilder defines building a ParSeq Trace Result using the [[ParSeqTraceRenderer]] if the
 * [[ParSeqTraceSensor]] determines ParSeq Trace is enabled.
 *
 * @author Yinan Ding (yding@linkedin.com)
 */
trait ParSeqTraceBuilder {

  /**
   * The method build builds the ParSeq Trace through the decision of [[ParSeqTraceSensor]] and the output of
   * [[ParSeqTraceRenderer]].
   *
   * @param origin The origin Future of Result
   * @param parSeqTaskStore The [[ParSeqTaskStore]] for getting ParSeq Tasks
   * @param parSeqTraceSensor The [[ParSeqTraceSensor]] for deciding whether ParSeq Trace is enabled or not
   * @param parSeqTraceRenderer The [[ParSeqTraceRenderer]] for generating the ParSeq Trace page
   * @param requestHeader The request
   * @return The Future of Result
   */
  def build(origin: Future[Result], parSeqTaskStore: ParSeqTaskStore, parSeqTraceSensor: ParSeqTraceSensor, parSeqTraceRenderer: ParSeqTraceRenderer)(implicit requestHeader: RequestHeader): Future[Result]

}

/**
 * The class ParSeqTraceBuilderImpl is an implementation of [[ParSeqTraceBuilder]] with the help from the class
 * [[PlayParSeqTraceHelper]].
 *
 * @param materializer The injected [[Materializer]] component
 * @param executionContext The injected [[ExecutionContext]] component
 * @author Yinan Ding (yding@linkedin.com)
 */
@Singleton
class ParSeqTraceBuilderImpl @Inject()(implicit materializer: Materializer, executionContext: ExecutionContext) extends PlayParSeqTraceHelper with ParSeqTraceBuilder {

  /**
   * @inheritdoc
   */
  override def build(origin: Future[Result], parSeqTaskStore: ParSeqTaskStore, parSeqTraceSensor: ParSeqTraceSensor, parSeqTraceRenderer: ParSeqTraceRenderer)(implicit requestHeader: RequestHeader): Future[Result] = {
    // Sense
    if (parSeqTraceSensor.isEnabled(parSeqTaskStore)) {
      // Consume the origin and bind independent Tasks
      val futures: Set[Future[Any]] = Set(origin.flatMap(consumeResult)) ++ parSeqTaskStore.get.map(bindTaskToFuture(_))
      // Render
      Future.sequence(futures).flatMap(_ => parSeqTraceRenderer.render(parSeqTaskStore))
    } else origin
  }

}

/**
 * The class ParSeqTraceAction is an Action composition which sets up a normal Request with [[ParSeqTaskStore]] in order
 * to put ParSeq Task into store for retrieving all Tasks within the scope of one request when building ParSeq Trace.
 * And it also composes with [[ParSeqTraceBuilder]] to hand origin Result off in order to determine whether to show
 * ParSeq Trace data or the origin Result, if so generate the ParSeq Trace Result.
 *
 * @param parSeqTaskStore The injected [[ParSeqTaskStore]] component
 * @param parSeqTraceBuilder The injected [[ParSeqTraceBuilder]] component
 * @param parSeqTraceSensor The injected [[ParSeqTraceSensor]] component
 * @param parSeqTraceRenderer The injected [[ParSeqTraceRenderer]] component
 * @param parser The injected [[BodyParser]] component
 * @param executionContext The injected [[ExecutionContext]] component
 * @author Yinan Ding (yding@linkedin.com)
 */
class ParSeqTraceAction @Inject()(parSeqTaskStore: ParSeqTaskStore, parSeqTraceBuilder: ParSeqTraceBuilder, parSeqTraceSensor: ParSeqTraceSensor, parSeqTraceRenderer: ParSeqTraceRenderer, parser: BodyParsers.Default)(implicit executionContext: ExecutionContext) extends ActionBuilderImpl(parser) {

  /**
   * The method invokeBlock sets up a normal Request with [[ParSeqTaskStore]] and composes with [[ParSeqTraceBuilder]]
   * to build ParSeq Trace for the Request.
   *
   * @param request The origin Request
   * @param block The block of origin Request process
   * @tparam A The type parameter of the Request
   * @return The Future of Result
   */
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    // Initialize the store
    implicit val newRequest = parSeqTaskStore.initialize(request)
    // Compose
    parSeqTraceBuilder.build(block(newRequest), parSeqTaskStore, parSeqTraceSensor, parSeqTraceRenderer)
  }

}
