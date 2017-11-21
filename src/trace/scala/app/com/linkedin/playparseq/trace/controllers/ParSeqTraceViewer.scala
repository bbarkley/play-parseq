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
package com.linkedin.playparseq.trace.controllers

import com.linkedin.parseq.{Engine, GraphvizEngine, HttpResponse, Task}
import com.linkedin.playparseq.s.PlayParSeqImplicits._
import com.linkedin.playparseq.utils.PlayParSeqHelper
import controllers.Assets
import java.io.ByteArrayInputStream
import java.nio.file.{Files, Path}
import javax.inject.{Inject, Singleton}
import javax.servlet.http.HttpServletResponse
import org.apache.commons.io.FileUtils
import play.api.Configuration
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Result}
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process
import scala.util.{Failure, Success, Try}


/**
 * The class ParSeqTraceViewer is a Controller to generate ParSeq Trace page with dot file and manage all the ParSeq
 * Trace resources.
 *
 * @param engine The injected ParSeq Engine component
 * @param applicationLifecycle The injected ApplicationLifeCycle component
 * @param configuration The injected Configuration component
 * @param assets The injected Assets Controller
 * @param controllerComponents The injected Controller component
 * @param executionContext The injected [[ExecutionContext]] component
 * @author Yinan Ding (yding@linkedin.com)
 */
@Singleton
class ParSeqTraceViewer @Inject()(engine: Engine, applicationLifecycle: ApplicationLifecycle, configuration: Configuration, assets: Assets, val controllerComponents: ControllerComponents)(implicit executionContext: ExecutionContext) extends PlayParSeqHelper with BaseController {

  /**
   * A happy logger.
   */
  private val logger = Logger(classOf[ParSeqTraceViewer])

  /**
   * The field cachePath is the file path of the ParSeq Trace cache directory.
   */
  private lazy val cachePath: Path = Files.createTempDirectory("cache")

  /**
   * The field graphvizEngine is for generating graphviz files.
   */
  private lazy val graphvizEngine: GraphvizEngine = new GraphvizEngine(getDotLocation, cachePath, getCacheSize, getTimeoutMilliseconds, getParallelLevel, getDelayMilliseconds, getProcessQueueSize)

  /**
   * The field setup is for starting the GraphvizEngine and hooking cleanup to application lifecycle.
   */
  private lazy val setup = {
    // Start the GraphvizEngine
    graphvizEngine.start()
    // Add stop hook
    applicationLifecycle.addStopHook(() => Future {
      // Stop the GraphvizEngine
      graphvizEngine.stop()
      // Clear cache directory
      FileUtils.deleteDirectory(cachePath.toFile)
    })
  }

  /**
   * The method at returns the ParSeq Trace resource file.
   *
   * @param file The file name
   * @return The Action
   */
  def at(file: String): Action[AnyContent] = {
    setup
    if (file.startsWith("cache")) {
      // Cache file
      Action {
        Ok.sendFile(cachePath.resolve(file.split("/")(1)).toFile)
      }
    } else {
      // Resource file
      assets.at("/tracevis", file)
    }
  }

  /**
   * The method dot generates graphviz files and returns the build response as result.
   *
   * @return The Action
   */
  def dot: Action[AnyContent] = Action.async(request => {
    // Get hash value
    val hash = request.getQueryString("hash").orNull
    // Get body info
    val body = request.body.asText.map((b) => new ByteArrayInputStream(b.getBytes)).orNull
    // Build files
    val task: Task[Result] = graphvizEngine.build(hash, body).map((response: HttpResponse) => {
      // Generate Result
      response.getStatus.intValue match {
        case HttpServletResponse.SC_OK => Ok(response.getBody)
        case HttpServletResponse.SC_BAD_GATEWAY => BadRequest(response.getBody)
        case _ => InternalServerError(response.getBody)
      }
    })
    // Run task
    val result = bindTaskToFuture(task)
    engine.run(task)
    result
  })

  /**
   * The methods getDotLocation gets the file path of the dot from conf file, otherwise it will get from the system.
   *
   * @return The file path
   */
  private[this] def getDotLocation: String = configuration.getOptional[String]("parseq.trace.docLocation").getOrElse(Try {
    System.getProperty("os.name").toLowerCase match {
      case u if u.indexOf("mac") >= 0 || u.indexOf("nix") >= 0 || u.indexOf("nux") >= 0 || u.indexOf("aix") >= 0 => process.stringToProcess("which dot").!!.trim
      case w if w.indexOf("win") >= 0 => process.stringToProcess("where dot").!!.trim
      case _ => null
    }
  } match {
    case Success(value) => value
    case Failure(_) => logger.error("No executable for dot found. See http://www.graphviz.org."); null
  })

  /**
   * The method getCacheSize gets the number of cache items in the GraphvizEngine from conf file, otherwise it will
   * generate a default value, which is 1024.
   *
   * @return The number of cache
   */
  private[this] def getCacheSize: Int = configuration.getOptional[Int]("parseq.trace.cacheSize").getOrElse(1024)

  /**
   * The method getTimeoutSeconds gets the timeout of the GraphvizEngine execution in the unit of milliseconds from conf
   * file, otherwise it will generate a default value, which is 5000.
   *
   * @return The timeout in milliseconds
   */
  private[this] def getTimeoutMilliseconds: Long = configuration.getOptional[Long]("parseq.trace.timeoutMilliseconds").getOrElse(5000)

  /**
   * The method getParallelLevel gets the maximum of the GraphvizEngine's parallel level from conf file, otherwise it
   * will generate a default value, which is the number of available processors.
   *
   * @return The parallel level
   */
  private[this] def getParallelLevel: Int = configuration.getOptional[Int]("parseq.trace.parallelLevel").getOrElse(Runtime.getRuntime.availableProcessors)

  /**
   * The method getDelayMilliseconds gets the delay time between different executions of the GraphvizEngine in the unit
   * of milliseconds from conf file, otherwise it will generate a default value, which is 5.
   *
   * @return The delay time in milliseconds
   */
  private[this] def getDelayMilliseconds: Long = configuration.getOptional[Long]("parseq.trace.delayMilliseconds").getOrElse(5)

  /**
   * The method getProcessQueueSize gets the size of the GraphvizEngine's process queue from conf file, otherwise it
   * will generate a default value, which is 1000.
   *
   * @return The size of process queue
   */
  private[this] def getProcessQueueSize: Int = configuration.getOptional[Int]("parseq.trace.processQueueSize").getOrElse(1000)

}
