//a Jenkinsfile is a text file that contains the definition of a Jenkins Pipeline and is checked into source control. 

properties( [
  buildDiscarder( logRotator( numToKeepStr: '15', artifactNumToKeepStr: '3') ), //discard old builds
  disableConcurrentBuilds(), //limit the concurrent builds
  //ask for multiple choice items, and stack up the input required
 parameters([ 
   choice(name: 'DEPLOY_ENV', choices: ['lp-nextgen', 'lp-test'],  description: 'The target environment' ),
   choice(name: 'MY_SLACK_CHANNEL', choices: ['#jenkins', '#notifications-test'], description: 'Slack Notification' ),
   choice(name: 'SPRING_PROFILE', choices: ['qa', 'docker'], description: 'Spring Profile for Deployment Environment' )
 ])
])

timestamps {
  //after 60 minutes the build is aborted if not concluded
  timeout(time: 60, unit: 'MINUTES') {
    //node is a crucial first step as it allocates an executor and workspace for the Pipeline
	node('master') { 
	//Specifying the versions, credentials, urls:
	  JAVA_VERSION = '/usr/local/jdk-11.0.2'
	  NEXUS_CREDENTIALS_ID = '7e2ce399-5f82-4572-9d9b-5e2aa3fdf100'
	  NEXUS_RELEASES_REPO_URL = 'http://35.197.25.236:8081/repository/maven-releases'
	  NEXUS_SNAPSHOTS_REPO_URL = 'http://35.197.25.236:8081/repository/maven-snapshots'
		GIT_CREDENTIALS_ID = 'f3caa026-0bee-4c7d-b575-3ec0b92cb064'
	  cleanWs() // deleteDir ensures fresh checkouts of repos each time
	  //The checkout step will checkout code from source control. scm is a special variable which instructs the checkout step to clone the specific revision which triggered this Pipeline run
	  checkout scm 
	  //getting the git url [Note: it must be done after checkout scm. Basically, you must be in a checked out git repository (i.e. has .git/config file in it)]
	  REPO_URL = sh(returnStdout: true, script: 'git config remote.origin.url').trim()

	  try {
		currentBuild.result = 'SUCCESS'
		//A stage block defines a conceptually distinct subset of tasks performed through the entire Pipeline (e.g. "Build", "Test" and "Deploy" stages), which is used by many plugins to visualize or present Jenkins Pipeline status/progress.
		//Build Stage
		stage('Gradle Docker Build') {
			sh 'echo $MY_SLACK_CHANNEL'
			sh 'sudo ./gradlew ' +
		   "-Dorg.gradle.java.home=$JAVA_VERSION " + //specifying which java version to use
		   "clean :payment-service-api:build docker" 
		}
		stage('Gradle Quality Gate') {
			sh 'sudo ./gradlew ' +
		   "-Dorg.gradle.java.home=$JAVA_VERSION " +
		   "clean sonarqube"
      
		}
		//Pushing docker image
		stage('Push Docker image')
		{
		  	//Attempt to Give access to the LP_GCR service account.  Don't touch the account, but I created it here.
			  withCredentials([[$class: 'FileBinding', credentialsId: '538c31e2-6999-48f0-8a1f-98ac09e3a8c5', variable: 'LP_GCR_SERVICE_ACCOUNT']]) {
			  	sh '''
				sudo gcloud auth activate-service-account --key-file $LP_GCR_SERVICE_ACCOUNT
			  	sudo gcloud config set project lp-gcr
			  	sudo chown -R jenkins:jenkins /var/lib/jenkins/
				sudo docker-credential-gcr configure-docker
			  	sudo docker tag com.littlepassports/payment-service-api:latest gcr.io/lp-gcr/payment-service:${BUILD_TIMESTAMP}
				sudo docker push gcr.io/lp-gcr/payment-service:${BUILD_TIMESTAMP}
				'''
			  }
			
		}
		//Then authorize with Nextgen GCP service account to deploy to kubernetes.
		//Deploy Stage
		stage('Deploy to Kube clusters')
		  {
			def jenkinsCommon = load "./jenkins/common.groovy"
		   	jenkinsCommon.deployLpmage();
			if ( DEPLOY_ENV == 'lp-nextgen') {
			sh '''
			response=$(curl --write-out %{http_code} --silent --output /dev/null http://35.233.175.56:8070/payment-service/swagger-ui.html)
			if [ $response = '200' ];
			then 
				echo "Deployment success";
			else 
				"exit -1";
			fi
			'''
			}
		  }
		    stage('Payment Testsuite Execution'){
			checkout([$class: 'GitSCM',
			  branches: [[name: '*/master']],
			  userRemoteConfigs: [
				[url: 'git@github.com:LittlePassports/LittlePassport_Api-AutomationRepo.git',
				 credentialsId: GIT_CREDENTIALS_ID]
			  ]
			])  
			 sh 'sudo chmod 755 gradlew'
                         sh 'sudo ./gradlew ' +
                       "-Dorg.gradle.java.home=$JAVA_VERSION " +
                       "test -Ppayment"
			 publishHTML([reportDir: 'test-output', reportFiles: 'PaymentServiceReport.html', reportName: 'Payment Test-suite Report'])
		  }
	  }
		catch (e) {
		currentBuild.result = "FAILURE"
		println(e)
		throw e
	  } finally {
		publishHTML([reportDir: 'payment-service-api/build/reports/tests/test', reportFiles: 'index.html', reportName: ' Gradle Test Report'])
		//Run slack and github notifications.
		dir('service-commons') {
		  checkout([$class: 'GitSCM', branches: [[name: 'master']], userRemoteConfigs: [[url: 'https://github.com/LittlePassports/service-commons.git', credentialsId: '7ea66dbf-14d8-4f86-b48c-3b43498737f1']]])
		  def slackCommon = load "./jenkins/common/slack.groovy"
		  def scmNotificationCommon = load "./jenkins/common/scmNotification.groovy"
		  slackCommon.sendSlackNotification(currentBuild.result);
		  scmNotificationCommon.sendBuildStatusToScm(REPO_URL, currentBuild.result);
		}
	  }
  }
}
}
