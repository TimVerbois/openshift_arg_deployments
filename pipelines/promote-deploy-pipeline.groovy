def buildTemplate = "templates/build.yaml"
def serviceTemplate = "templates/service.yaml"
def deploymentTemplate = "templates/deployment.yaml"
def routeTemplate = "templates/route.yaml"
def configMapTemplate = "templates/configmap.yaml"

//def applicationName = env.APPLICATION_NAME
def applicationName = "helloworld2"
def from_project = env.FROM_PROJECT
def version = env.version
def applicationCn = env.APPLICATION_CN

if ( version == "" ) {
    echo message: "WARNING: no version has been provided, reverting to version latest"
    version = "latest"
}

pipeline {
  agent {
    node {
      label 'master'
    }
  }
  options {
    timeout(time: 20, unit: 'MINUTES') 
  }
  stages {
/*    stage('cleanup') {
      steps {
        script {
            openshift.withCluster() {
                openshift.withProject() {
                  if ( openshift.selector("bc", applicationName).exists() ) {
                    openshift.selector("bc", applicationName).delete()
                  }
                  if ( openshift.selector("dc", applicationName).exists() ) {
                    openshift.selector("dc", applicationName).delete()
                  }
                  if ( openshift.selector("service", applicationName).exists() ) {
                    openshift.selector("service", applicationName).delete()
                  }
                  if ( openshift.selector("route", applicationName).exists() ) {
                    openshift.selector("route", applicationName).delete()
                  }
                  if ( openshift.selector("cm", applicationName).exists() ) {
                    openshift.selector("cm", applicationName).delete()
                  }
                }
            }
        }
      }
   }*/
   stage('CreateConfig') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
              //def configMap = readFile(configMapTemplate).replaceAll("..VERSION.", version).replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
              def configMap = readFile(configMapTemplate).replaceAll("..VERSION.", version)
              echo message: configMap
              openshift.create(configMap)
            }
          }
        }
      }
    }
   stage('promote') {
     steps {
       script {
         openshift.withCluster() {
           openshift.withProject() {
             withDockerRegistry([url: "docker-registry.default.svc:5000/ci00000000-argentatest-01"]) {
               withDockerRegistry([url: "docker-registry.default.svc:5000/ci00000000-argentatest-02"]) {
                 sh """
                    oc image mirror docker-registry.default.svc:5000/ci00000000-argentatest-01/helloworld2:${version} docker-registry.default.svc:5000/ci00000000-argentatest-02/helloworld2:${version}
                 """
               }
             }
           }
         }
       }
     }
   }
   stage('deployment') {
     steps {
       script {
         openshift.withCluster() {
           openshift.withProject() {
              def deploymentConfig = readFile(deploymentTemplate).replaceAll("..VERSION.", version).replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
              if ( openshift.selector("dc", applicationName).exists() ) {
                openshift.apply(deploymentConfig)
              }
              def rm = openshift.selector("dc", applicationName).rollout()
              timeout(5) { 
                openshift.selector("dc", applicationName).related('pods').untilEach(1) {
                  return (it.object().status.phase == "Running")
                }
              }
            }
          }
        }
      }
    }
    stage('expose') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
              def serviceConfig = readFile(serviceTemplate).replaceAll("..VERSION.", "latest").replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
              if ( openshift.selector("service", applicationName).exists()) {
                openshift.apply(serviceConfig)
              }
              else {
                openshift.create(serviceConfig)
              }
              def routeConfig = readFile(routeTemplate).replaceAll("..VERSION.", "latest").replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())replaceAll("..APPLICATION_CN.", applicationCn)
              if ( openshift.selector("rc", applicationName).exists()) {
                openshift.apply(routeConfig)
              }
              else {
                openshift.create(routeConfig)
              }
            }
          }
        }
      }
    }
  }
}
