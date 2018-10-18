def buildTemplate = "templates/build.yaml"
def serviceTemplate = "templates/service.yaml"
def deploymentTemplate = "templates/deployment.yaml"
def routeTemplate = "templates/route.yaml"
def configMapTemplate = "templates/configmap.yaml"

def applicationName = "helloworld"
def version = env.version
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
    stage('cleanup') {
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
   }
    stage('build') {
      steps {
        script {
            openshift.withCluster() {
                openshift.withProject() {
                  openshift.create(readFile(buildTemplate))
                  def builds = openshift.selector("bc", applicationName).related('builds')
                  timeout(5) {
                    builds.untilEach(1) {
                      return (it.object().status.phase == "Complete")
                    }
                  }
                }
            }
        }
      }
    }
    stage('test') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
              def deploymentConfig = readFile(deploymentTemplate).replaceAll("..VERSION.", "latest").replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
              openshift.create(deploymentConfig)
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
    stage('tag') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
              def imagestream = openshift.selector("is", applicationName)
              openshift.tag("${applicationName}:latest", "${applicationName}:${version}") 
            }
          }
        }
      }
    }
    stage('deploy') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
              if ( openshift.selector("dc", applicationName).exists() ) {
                openshift.selector("dc", applicationName).delete()
              }
              def configMap = readFile(configMapTemplate).replaceAll("..VERSION.", version).replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
              openshift.create(configMap)
              def deploymentConfig = readFile(deploymentTemplate).replaceAll("..VERSION.", version).replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
              openshift.create(deploymentConfig)
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
                  openshift.create(serviceConfig)
                  def routeConfig = readFile(routeTemplate).replaceAll("..VERSION.", "latest").replaceAll("..APPLICATION_NAME.", applicationName).replaceAll("..NAMESPACE.", openshift.project())
                  openshift.create(routeConfig)
                }
            }
        }
      }
    }
   }
}
