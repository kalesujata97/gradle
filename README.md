This is a simple Servlet webapp that uses a Gradle to manage the app.


Run this app locally 
--------------------

Run in a Jetty container

    $ gradle jettyRunWar


Build the app WAR file
----------------------

    $ gradle war 


Deploy the WAR file to CloudBees
--------------------------------

It is recommended to set your CloudBees credentials in your ~/.gradle/gradle.properties file and your appId in your build.gradle file.

~/.gradle/gradle.properties

    cloudbees.api.key = yourApiKey
    cloudbees.api.secret = yourApiSecret

build.gradle

    cloudBees {
      appId = 'YOUR_APP_ID'
    }

Deploy the WAR file using the cloudbees plugin

    $ gradle war
    $ gradle cloudBeesAppDeployWar

Alternatively, deploy by specifying your API key+secret and appID on command line:

    $ gradle cloudBeesAppDeployWar -Pcloudbees.api.key=YOUR_API_KEY -Dcloudbees.api.secret=YOUR_API_SECRET -DappId=YOUR_APP_ID


