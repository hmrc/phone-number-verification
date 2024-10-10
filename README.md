## phone-number-verification

### Summary

Backend server for validating and verifying a given phone number

The default port for phone-number-frontend is 6080
The default port for phone-number is port 6081
The default port for phone-number-verification is port 6083
The default port for phone-number-stubs is port 6099

### Testing

#### Unit tests

    sbt clean test

#### Integration tests

    sbt clean it:test

### Running app

    sm --start PHONE_NUMBER_VERIFICATION_ALL

Run the services against the current versions in dev, stop the PHONE_NUMBER_VERIFICATION service and start manually

    sm --start PHONE_NUMBER_VERIFICATION_ALL -r
    sm --stop PHONE_NUMBER_VERIFICATION
    cd phone-number-verification
    sbt run

For reference here are the details for running each of the services individually

    cd phone-number-frontend
    sbt run
 
    cd phone-number
    sbt run

    cd phone-number-verification
    sbt run

    cd phone-number-stubs
    sbt run

### Curl microservice (for curl microservice build jobs)

#### Send Code

    -XPOST -H "Content-type: application/json" -d '{
	    "phoneNumber": "<phone-number>"
    }' 'https://phone-number-verification.protected.mdtp/phone-number-verification/send-code'

#### Verify Code

    -XPOST -H "Content-type: application/json" -d '{
	    "phoneNumber": "<phone-number>",
        "verificationCode": "<verification-code>"
    }' 'https://phone-number-verification.protected.mdtp/phone-number-verification/verify-code'

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
