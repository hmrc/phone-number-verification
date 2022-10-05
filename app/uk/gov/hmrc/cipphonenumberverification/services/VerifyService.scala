/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cipphonenumberverification.services

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.ServiceUnavailable
import uk.gov.hmrc.cipphonenumberverification.config.AppConfig
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.metrics.MetricsService
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Codes
import uk.gov.hmrc.cipphonenumberverification.models.api.ErrorResponse.Message.SERVER_CURRENTLY_UNAVAILABLE
import uk.gov.hmrc.cipphonenumberverification.models.api.{ErrorResponse, PhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.models.domain.data.PhoneNumberAndOtp
import uk.gov.hmrc.cipphonenumberverification.utils.DateTimeUtils
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class VerifyService @Inject()(otpService: OtpService,
                              auditService: AuditService,
                              passcodeService: PasscodeService,
                              validateConnector: ValidateConnector,
                              govUkConnector: GovUkConnector,
                              metricsService: MetricsService,
                              dateTimeUtils: DateTimeUtils,
                              config: AppConfig)
                             (implicit val executionContext: ExecutionContext)
  extends VerifyHelper(otpService, auditService, passcodeService, govUkConnector, metricsService, dateTimeUtils, config) {

  def verifyPhoneNumber(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier): Future[Result] =
    validateConnector.callService(phoneNumber.phoneNumber) transformWith {
      case Success(httpResponse) => processResponse(httpResponse)
      case Failure(error) =>
        metricsService.recordMetric("CIP-Validation-HTTP-Failure")
        metricsService.recordMetric(error.toString.trim.dropRight(1))
        logger.error(error.getMessage)
        Future.successful(ServiceUnavailable(Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVICE_FAIL, SERVER_CURRENTLY_UNAVAILABLE))))
    }

  def verifyOtp(phoneNumberAndOtp: PhoneNumberAndOtp)(implicit hc: HeaderCarrier): Future[Result] = {
    validateConnector.callService(phoneNumberAndOtp.phoneNumber).transformWith {
      case Success(httpResponse) => processResponseForOtp(httpResponse, phoneNumberAndOtp)
      case Failure(error) =>
        metricsService.recordMetric("CIP-Validation-HTTP-Failure")
        metricsService.recordMetric(error.toString.trim.dropRight(1))
        logger.error(error.getMessage)
        Future.successful(ServiceUnavailable(Json.toJson(ErrorResponse(Codes.EXTERNAL_SERVICE_FAIL, SERVER_CURRENTLY_UNAVAILABLE))))
    }
  }
}
