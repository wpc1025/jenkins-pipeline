def call(Map pipelineParams) {
    pipeline {
        agent any

        environment {
            //代码仓库地址
            scmUrl = "${pipelineParams.scmUrl}"
            //分支
            branch = "${pipelineParams.branch}"
            //eureka地址
            eurekaAddress = "${pipelineParams.eurekaAddress}"
            //代码仓库检出的账号密码在Jenkins中配置的标示
            codeCredentialsId = "${pipelineParams.codeCredentialsId}"

            //远程服务器用户名
            serverUserName = "${pipelineParams.serverUserName}"
            //远程服务器IP地址
            serverIp = "${pipelineParams.serverIp}"
            //远程服务器中存放服务实例的位置
            serverServiceRootFolder = "${pipelineParams.serverServiceRootFolder}"
            //存储远程服务器密码的文件路径
            serverPassWordFile = "${pipelineParams.serverPassWordFile}"

            //服务名称
            serviceName = "${pipelineParams.serviceName}"
            //服务在eureka中的ID
            serviceId = "${pipelineParams.serviceId}"
            //代码所用环境
            profile = "${pipelineParams.profile}"

            killShell = "ps -ef | grep ${pipelineParams.serviceName}.jar | grep -v grep | awk '{print \$2}'  | sed -e \"s/^/kill -9 /g\" | sh -"
        }

        stages {

            //下架服务
            stage('offLine') {
                steps {
                    sh "curl -X PUT ${eurekaAddress}" + "eureka/apps/" + "${serviceName}" + "/" + "${serviceId}" + "/status?value=DOWN"
                }
            }

            //资源释放
            stage('wait') {
                steps {
                    sleep 0
                }
            }

            //代码检出
            stage('checkOutCode') {
                steps {
                    git branch: "${branch}", changelog: false, credentialsId: "${codeCredentialsId}", poll: false, url: "${scmUrl}"
                }
            }

            //单元测试
            stage('test') {
                steps {
                    echo 'test'
                    //sh 'mvn clean test'
                }
            }

            //打包
            stage('package') {
                steps {
                    sh 'mvn clean package -Dmaven.test.skip=true'
                }
            }

            //发布服务到服务器中并启动
            stage('deploy') {

                steps {

                    //关闭原服务
                    sh "/usr/local/bin/sshpass -f ${serverPassWordFile} ssh " + "${serverUserName}@${serverIp}" + " 'sh ${killShell}'"
                    //上传新的jar包到服务器中
                    sh "/usr/local/bin/sshpass -f ${serverPassWordFile} scp  ${serviceName}/target/${serviceName}.jar " + "${serverUserName}" + "@" + "${serverIp}" + ":" + "${serverServiceRootFolder}${serviceName}/"
                    //启动服务
                    sh "/usr/local/bin/sshpass -f ${serverPassWordFile} ssh " + "${serverUserName}@${serverIp}" + " 'nohup java -jar ${serverServiceRootFolder}${serviceName}/${serviceName}.jar ${profile} > /dev/null &'"
                }
            }

            //检测服务是否启动正常
            stage('checkServiceStatus') {
                steps {
                    echo 'checkServiceStatus'
                }
            }

            //服务在eureka中上线
            stage('deleteService') {
                steps {
                    sh "curl -X DELETE ${eurekaAddress}" + "eureka/apps/" + "${serviceName}" + "/" + "${serviceId}"
                }
            }

        }

    }

}