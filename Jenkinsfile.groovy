node{

  stage ('Build') {
   
    git credentialsId: 'ea4c3770-b2ed-4639-9ffc-cc3e586e454c', url: 'https://github.com/SujataKale97/gradle-web-app.git' 
        
    bat 'gradle clean build'
    
  //  bat '''copy C:\\Apps\\Jenkins\\jobs\\gradle-pipeline\\workspace\\build\\libs\\*.war C:\\Apps\\Jenkins\\jobs\\gradle-pipeline\\workspace'''
      
  }
  stage('Deploy'){
 //   bat 'docker build -t mywebapp  .'
  }
  
  stage('Test'){
    
    checkout([$class: 'GitSCM',
			  branches: [[name: '*/master']],
			  userRemoteConfigs: [
				[url: 'https://github.com/SujataKale97/webapp.git',
				 credentialsId: 'ea4c3770-b2ed-4639-9ffc-cc3e586e454c' ]
			  ]
			])  
    
    bat 'gradle test -Ppay'
  }
 
}
