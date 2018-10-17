//def templatePath = 'https://raw.githubusercontent.com/openshift/nodejs-ex/master/openshift/templates/nodejs-mongodb.json' 
def templatePath = "templates/build.yaml"
//def applicationName = 'nodejs-mongodb-example' 
def applicationName = "helloworld"
def version = env.version
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
    stage('preamble') {
        steps {
            script {
                openshift.withCluster() {
                    openshift.withProject() {
                        echo "Using project: ${openshift.project()}"
                    }
                }
            }
        }
    }
    stage('cleanup') {
      steps {
        script {
            openshift.withCluster() {
                openshift.withProject() {
                  openshift.selector("bc", applicationName).delete()
                }
            }
        }
      }
    }
    stage('create') {
      steps {
        script {
            openshift.withCluster() {
                openshift.withProject() {
                  openshift.create("-f " + templatePath) 
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
    stage('deploy') {
      steps {
        script {
          openshift.withCluster() {
            openshift.withProject() {
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
  }
}
