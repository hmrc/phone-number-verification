## cip-phone-number-verification

### Summary

Backend server for validating and verifying a given phone number

### Testing

#### Unit tests

    sbt clean test

#### Integration tests

    sbt clean it:test

### Running app

    sm --start CIP_PHONE_NUMBER_VERIFICATION_ALL

Run the services against the current versions in dev, stop the CIP_PHONE_NUMBER_VERIFICATION service and start manually

    sm --start CIP_PHONE_NUMBER_VERIFICATION_ALL -r
    sm --stop CIP_PHONE_NUMBER_VERIFICATION
    cd cip-phone-number-verification
    sbt 'run 6083'

For reference here are the details for running each of the services individually

    cd cip-phone-number-frontend
    sbt 'run 6080'
 
    cd cip-phone-number
    sbt 'run 6081'

    cd cip-phone-number-validation
    sbt 'run 6082'

    cd cip-phone-number-verification
    sbt 'run 6083'

### License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
