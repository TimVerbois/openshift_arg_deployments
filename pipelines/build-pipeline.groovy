def buildTemplate = "templates/build.yaml"
def serviceTemplate = "templates/service.yaml"
def deploymentTemplate = "templates/deployment.yaml"

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
                  if ( openshift.selector("bc", applicationName.exists()) ) {
                    openshift.selector("bc", applicationName).delete()
                  }
                  if ( openshift.selector("dc", applicationName.exists()) ) {
                    openshift.selector("dc", applicationName).delete()
                  }
                  if ( openshift.selector("service", applicationName.exists()) ) {
                    openshift.selector("service", applicationName).delete()
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
              def deploymentConfig = readFile(deploymentTemplate).replaceAll('\${VERSION}', "latest").replaceAll('\${APPLICATION_NAME}', applicationName)
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
              imagestream.describe()
              openshift.tag("${applicationName}:latest", "${applicationName}:${version}") 
            }
          }
        }
      }
    }
    stage('redeploy') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
              openshift.selector("dc", applicationName).delete()
              def deploymentConfig = readFile(deploymentTemplate).replaceAll('\${VERSION}', version).replaceAll('\${APPLICATION_NAME}', applicationName)
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
                  openshift.create(readFile(serviceTemplate))
                }
            }
        }
      }
    }
   }
}
