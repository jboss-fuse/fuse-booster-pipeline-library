/*
 * Copyright (C) 2018 Red Hat, Inc.
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
def call(imageToImport=null) {
  properties([
    [
      $class: 'BuildDiscarderProperty',
      strategy: [$class: 'LogRotator', daysToKeepStr: '10', numToKeepStr: '5']
    ]
  ])

  node('docker') {
    timeout(time: 1, unit: 'HOURS') {
      timestamps {
        ansiColor('xterm') {
          checkout scm

            def oc_home = tool 'oc-v3.9.0-191fece'

            stage('Build') {
              sh './mvnw -B -V clean verify -DskipTests'
            }

          stage('Test') {
            try {
              sh './mvnw -B -V test'
            } finally {
              junit(testResults: '**/target/*-reports/*.xml', allowEmptyResults: true)
            }
          }

          stage('Integration Test') {
            sh "${oc_home}/oc cluster up"
            try {
              if(imageToImport!=null){
                sh "${oc_home}/oc import-image fuse-java-openshift:1.2 --from="+imageToImport+" --confirm"
                sh './mvnw -B -V -DskipTests fabric8:deploy -Popenshift -Dfabric8.generator.fromMode=istag -Dfabric8.generator.from=myproject/fuse-java-openshift:1.2'  
              } else {
                sh './mvnw -B -V -DskipTests fabric8:deploy -Popenshift'
              }
            } finally {
              sh "${oc_home}/oc cluster down"
            }
          }
          
          stage('Template Integration Test') {
            sh "${oc_home}/oc cluster up"
            try {
              if(imageToImport!=null){
                sh "${oc_home}/oc import-image fuse-java-openshift:1.2 --from="+imageToImport+" --confirm"
                sh './mvnw -B -V -Dtest=*KT -DfailIfNoTests=false clean test'
              } else {
                sh './mvnw -B -V -Dtest=*KT -DfailIfNoTests=false clean test'
              }
            } finally {
              sh "${oc_home}/oc cluster down"
              junit(testResults: '**/target/*-reports/*.xml', allowEmptyResults: true)
            }
          }
        }
      }
    }
  }
}

