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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc.Results.{BadRequest, Ok}
import uk.gov.hmrc.cipphonenumberverification.connectors.{GovUkConnector, ValidateConnector}
import uk.gov.hmrc.cipphonenumberverification.models.{Passcode, PhoneNumber}
import uk.gov.hmrc.cipphonenumberverification.repositories.PasscodeCacheRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.matching.Regex

class VerifyServiceSpec extends AnyWordSpec with Matchers with MockitoSugar{

  "Verify Service" should {
    "return success if telephone number is valid" in new SetUp {
      implicit val hc = new HeaderCarrier()
      when(validateConnectorMock.callService(any())(any(), any())).thenReturn(Future.successful(Ok))
      verifyService.verifyDetails(PhoneNumber("07856345678")) map { x =>
        assert(x === Ok)
      }
    }

    "return failure if telephone number is invalid" in new SetUp {
      implicit val hc = new HeaderCarrier()
      when(validateConnectorMock.callService(any[PhoneNumber]())(any(), any())).thenReturn(Future.successful(Ok))
      verifyService.verifyDetails(PhoneNumber("078563d45678")) map { x =>
        assert(x === BadRequest)
      }
    }

    "create 6 digit passcode" in new SetUp {

      verifyService.passcodeGenerator.forall(y => y.isUpper) shouldBe true
      verifyService.passcodeGenerator.forall(y => y.isLetter) shouldBe true

      val a = List('A', 'E', 'I', 'O', 'U')
      verifyService.passcodeGenerator.toList map (y => assertResult(a contains(y))(false))
      
      verifyService.passcodeGenerator.length shouldBe 6
    }
  }


  trait SetUp {
    val exception = new Throwable
    val passcodeCacheRepositoryMock = mock[PasscodeCacheRepository]
    val validateConnectorMock = mock[ValidateConnector]
    val govUkConnectorMock = mock[GovUkConnector]

    val verifyService = new VerifyService(passcodeCacheRepositoryMock, validateConnectorMock, govUkConnectorMock, mock[MessagesApi], mock[Langs])
  }
}
