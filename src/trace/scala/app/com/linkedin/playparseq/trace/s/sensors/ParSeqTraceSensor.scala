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
package com.linkedin.playparseq.trace.s.sensors

import com.linkedin.playparseq.s.stores.ParSeqTaskStore
import javax.inject.{Inject, Singleton}
import play.api.{Application, Mode}
import play.api.mvc.RequestHeader


/**
 * The trait ParSeqTraceSensor defines deciding whether to display the ParSeq Trace data or the origin Result based on
 * request and [[ParSeqTaskStore]].
 *
 * @author Yinan Ding (yding@linkedin.com)
 */
trait ParSeqTraceSensor {

  /**
   * The method isEnabled decides whether ParSeq Trace is enabled or not from request and [[ParSeqTaskStore]].
   *
   * @param requestHeader The request
   * @param parSeqTaskStore The [[ParSeqTaskStore]] for getting ParSeq Tasks
   * @return The decision
   */
  def isEnabled(requestHeader: RequestHeader, parSeqTaskStore: ParSeqTaskStore): Boolean
}

/**
 * The class ParSeqTraceSensorImpl is an implementation of the trait [[ParSeqTraceSensor]].
 * It determines based on whether the application is under dev mode, whether the query param is present and whether the
 * data from the [[ParSeqTaskStore]] is available.
 *
 * @param application The injected Application component
 * @author Yinan Ding (yding@linkedin.com)
 */
@Singleton
class ParSeqTraceSensorImpl @Inject()(application: Application) extends ParSeqTraceSensor {

  /**
   * The field QueryKey is the key of query for ParSeq Trace.
   */
  val QueryKey = "parseq-trace"

  override def isEnabled(requestHeader: RequestHeader, parSeqTaskStore: ParSeqTaskStore): Boolean =
    application.mode == Mode.Dev &&
      requestHeader.getQueryString(QueryKey).exists(_.equals("true")) &&
      parSeqTaskStore.get(requestHeader).nonEmpty
}
