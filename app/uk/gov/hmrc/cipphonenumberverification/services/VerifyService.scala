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

import play.api.mvc.Result
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.models._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class VerifyService @Inject()(otpService: OtpService,
                              auditService: AuditService,
                              passcodeService: PasscodeService,
                              validatorConnector: ValidateConnector,
                              govUkConnector: GovUkConnector)
                             (implicit val executionContext: ExecutionContext)
  extends VerifyHelper(otpService, auditService, passcodeService, govUkConnector) {

  def verifyPhoneNumber(phoneNumber: PhoneNumber)(implicit hc: HeaderCarrier): Future[Result] =
    for {
      httpResponse <- validatorConnector.callService(phoneNumber.phoneNumber)
      result <- processResponse(httpResponse)
    } yield result

  def verifyOtp(phoneNumberAndOtp: PhoneNumberAndOtp)(implicit hc: HeaderCarrier): Future[Result] = {
    for {
      httpResponse <- validatorConnector.callService(phoneNumberAndOtp.phoneNumber)
      result <- processResponseForOtp(httpResponse, phoneNumberAndOtp)
    } yield result
  }
}
