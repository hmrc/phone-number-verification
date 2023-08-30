/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cipphonenumberverification.circuitbreaker

import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import uk.gov.hmrc.http.HeaderCarrier

import java.lang.System._
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.{ExecutionContext, Future}

class UnhealthyServiceException(message: String) extends RuntimeException(message)

case class CircuitBreakerConfig(serviceName: String, numberOfCallsToTriggerStateChange: Int, unavailablePeriodDuration: Int, unstablePeriodDuration: Int) {}

case object CircuitBreakerConfig {

  private val defaultDuration: Int      = 5 * 60 * 1000 // 5 minutes
  private val defaultNumberOfCalls: Int = 4

  def apply(
    serviceName: String,
    numberOfCallsToTriggerStateChange: Option[Int] = None,
    unavailablePeriodDuration: Option[Int] = None,
    unstablePeriodDuration: Option[Int] = None
  ): CircuitBreakerConfig = apply(
    serviceName,
    numberOfCallsToTriggerStateChange.getOrElse(defaultNumberOfCalls),
    unavailablePeriodDuration.getOrElse(defaultDuration),
    unstablePeriodDuration.getOrElse(defaultDuration)
  )
}

sealed abstract private[circuitbreaker] class StateProcessor {
  def name: String

  def processCallResult(wasCallSuccessful: Boolean): Unit

  def stateAwareInvoke[T](f: => Future[T])(implicit hc: HeaderCarrier): Future[T] = f
}

sealed private[circuitbreaker] trait TimedState {
  def duration: Int

  private val periodStart = currentTimeMillis

  def periodElapsed: Boolean = currentTimeMillis - periodStart > duration
}

private[circuitbreaker] class CircuitBreaker(val config: CircuitBreakerConfig, exceptionsToBreak: Throwable => Boolean)(implicit ec: ExecutionContext) {

  getLogger.info(s"Circuit Breaker [$name] instance created with config $config")

  private val state = new AtomicReference(initialState)

  protected def initialState: StateProcessor = Healthy

  def name: String = config.serviceName

  def currentState: StateProcessor = state.get

  logState(currentState)

  def isServiceAvailable = currentState match {
    case unavailableState: Unavailable => unavailableState.periodElapsed
    case _                             => true
  }

  protected def getLogger = LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]

  private[circuitbreaker] def setState(oldState: StateProcessor, newState: StateProcessor) =
    /* If the state initiating a state change is no longer the current state
     * we ignore this call. We are sacrificing a tiny bit of accuracy in our counters
     * for getting full thread-safety with good performance.
     */
    if (state.compareAndSet(oldState, newState)) {
      logState(newState)
    }

  def logState(newState: StateProcessor): Unit =
    getLogger.warn(s"circuitbreaker: Service [$name] is in state [${newState.name}]")

  def invoke[T](f: => Future[T])(implicit hc: HeaderCarrier): Future[T] =
    currentState.stateAwareInvoke(f).map {
      x =>
        currentState.processCallResult(wasCallSuccessful = true)
        x
    } recoverWith {
      case unhealthyService: UnhealthyServiceException =>
        throw unhealthyService
      case ex: Throwable =>
        currentState.processCallResult(wasCallSuccessful = !exceptionsToBreak(ex))
        throw ex
    }

  sealed protected trait CountingState {
    def startCount: Int

    private val count = new AtomicInteger(startCount)

    def needsStateChangeAfterIncrement = count.incrementAndGet >= config.numberOfCallsToTriggerStateChange
  }

  protected object Healthy extends StateProcessor {

    def processCallResult(wasCallSuccessful: Boolean) =
      if (!wasCallSuccessful) {
        if (config.numberOfCallsToTriggerStateChange > 1) setState(this, new Unstable)
        else setState(this, new Unavailable)
      }

    val name = "HEALTHY"
  }

  protected class Unstable extends StateProcessor with TimedState with CountingState {

    lazy val startCount = 1
    lazy val duration   = config.unstablePeriodDuration

    def processCallResult(wasCallSuccessful: Boolean) =
      if (wasCallSuccessful && periodElapsed) setState(this, Healthy)
      else if (!wasCallSuccessful) {
        if (periodElapsed) setState(this, new Unstable) // resets count
        else if (needsStateChangeAfterIncrement) setState(this, new Unavailable)
      }

    val name = "UNSTABLE"
  }

  protected class Trial extends StateProcessor with CountingState {

    lazy val startCount = 0

    def processCallResult(wasCallSuccessful: Boolean) =
      if (wasCallSuccessful && needsStateChangeAfterIncrement) setState(this, Healthy)
      else if (!wasCallSuccessful) setState(this, new Unavailable)

    val name = "TRIAL"
  }

  protected class Unavailable extends StateProcessor with TimedState {

    lazy val duration = config.unavailablePeriodDuration

    def processCallResult(wasCallSuccessful: Boolean) = ()

    override def stateAwareInvoke[T](f: => Future[T])(implicit hc: HeaderCarrier): Future[T] =
      if (periodElapsed) {
        setState(this, new Trial)
        f
      } else {
        Future.failed(new UnhealthyServiceException(config.serviceName))
      }

    val name = "UNAVAILABLE"
  }

}

/** Trait to be mixed in to services or connectors that wish to
  *  protect their outgoing calls from wasting unsuccessful invocations
  *  in periods where the service seems to be unavailable.
  */
trait UsingCircuitBreaker {
  val ec: ExecutionContext

  /** The configuration for the circuit breaker:
    *
    * - `serviceName` - the name of the service
    * - `numberOfCallsToTriggerStateChange` - the number of failed calls that
    *   need to accumulate within `unstablePeriodDuration` for the service to get
    *   disabled, as well as the number of successful calls that are needed to
    *   get a service back from the disabled state to normal.
    * - `unavailablePeriodDuration` - the time in seconds that a service should
    *   be disabled in case it accumulated more than the configured maximum number
    *   of failures
    * - `unstablePeriodDuration` - the time in seconds that failure counts are
    *   accumulated. When the period ends without reaching the limit, the counter
    *   switches back to 0.
    */
  protected def circuitBreakerConfig: CircuitBreakerConfig

  /** Predicate that defines the exceptions that should be treated as a failure.
    *  In most cases only 5xx status responses should be treated as a server-side
    *  issue.
    */
  protected def breakOnException(t: Throwable): Boolean

  /** Indicates whether the service is available. Returns `false` if the service
    *  is disabled due to accumulating too many failures in the configured time
    *  frame. Note that due to the asynchronous nature of the circuit breaker,
    *  you can still get an `UnhealthyServiceException` after this method returned
    *  `true` as the state might change any time.
    */
  protected def isServiceAvailable = circuitBreaker.isServiceAvailable

  /** The `CircuitBreaker` instance used by this trait.
    */
  protected lazy val circuitBreaker = new CircuitBreaker(circuitBreakerConfig, breakOnException)(ec)

  /** Protects the specified future from being evaluated in case the service
    *  is disabled due to accumulating too many failures in the configured time
    *  frame. If the service is disabled, the future will fail with a `UnhealthyServiceException`,
    *  if it is enabled, it will succeed or fail with whatever result the original future produces.
    */
  protected def withCircuitBreaker[T](f: => Future[T])(implicit hc: HeaderCarrier): Future[T] = circuitBreaker.invoke(f)
}
