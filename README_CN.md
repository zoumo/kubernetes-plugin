# Jenkins-kubernetes-plugin

这个jenkins插件在默认情况下创建一个Kubernetes Pod作为jenkins的slave, 在pod中运行定义好的docker image来进行一次构建任务, 在构建完了之后将pod停止后删除
这个repo对插件进行了改进, 使得插件能够创建长期存在的pod.

slaves使用JNLP来自动连接jenkins master, 所以下列的环境变量会自动添加到每一个Container中:
* `JENKINS_URL`:Jenkins web interface url
* `JENKINS_JNLP_URL`: url for the jnlp definition of the specific slave
* `JENKINS_SECRET`: the secret key for authentication
* `JENKINS_NAME`: the name of the Jenkins agent

需要配合[jenkinsci/jnlp-slave](https://hub.docker.com/r/jenkinsci/jnlp-slave/)使用, 源码[Dockerfile](https://github.com/jenkinsci/docker-jnlp-slave)

# Pipeline support
Nodes 可以在pipeline中定义并且使用

```groovy
podTemplate(
    name: "mytest"
    label: 'mypod', 
    containers: [
        containerTemplate(name: 'jnlp', image: 'jenkinsci/jnlp-slave:2.62-alpine', args: '${computer.jnlpmac} ${computer.name}'),
        containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'golang', image: 'golang:1.6.3-alpine', ttyEnabled: true, command: 'cat')
    ],
    volumes: [secretVolume(secretName: 'shared-secrets', mountPath: '/etc/shared-secrets')]
)
{
    node ('mypod') { // 与上面的label一致
        stage('Get a Maven project') {
            git 'https://github.com/jenkinsci/kubernetes-plugin.git'
            container('maven') {
                stage 'Build a Maven project'
                sh 'mvn clean install'
            }
        }
        stage('Get a Golang project') {
            git url: 'https://github.com/hashicorp/terraform.git'
            container('golang') {
                stage 'Build a Go project'
                sh """
                mkdir -p /go/src/github.com/hashicorp
                ln -s `pwd` /go/src/github.com/hashicorp/terraform
                cd /go/src/github.com/hashicorp/terraform && make core-dev
                """
            }
        }


    }
}
```

# Container Configuration

```groovy
podTemplate(
    name: "mytest"
    label: 'mypod', 
    instanceCap: 1,
    containers: [
        containerTemplate(
            name: 'mariadb', 
            image: 'mariadb:10.1', 
            ttyEnabled: true, 
            command: 'cat'
            privileged: false,
            alwaysPullImage: false,
            workingDir: '/home/jenkins',
            args: '',
            resourceRequestCpu: '50m',
            resourceLimitCpu: '100m',
            resourceRequestMemory: '100Mi',
            resourceLimitMemory: '200Mi',
            envVars: [
                containerEnvVar(key: 'MYSQL_ALLOW_EMPTY_PASSWORD', value: 'true'),
                ...
            ]
        ),
        ...
    ],
    volumes: [
        emptyDirVolume(mountPath: '/etc/mount1', memory: false),
        secretVolume(mountPath: '/etc/mount2', secretName: 'my-secret'),
        configMapVolume(mountPath: '/etc/mount3', configMapName: 'my-config'),
        hostPathVolume(mountPath: '/etc/mount4', hostPath: '/mnt/my-mount'),
        nfsVolume(mountPath: '/etc/mount5', serverAddress: '127.0.0.1', serverPath: '/', readOnly: true),
        persistentVolumeClaim(mountPath: '/etc/mount6', claimName: 'myClaim', readOnly: true)
    ]
) {
    node(){
        ...
    }
   ...
}
```

# Always alive pod
只需要在podTemplate的中指定一个以`always-`或者`always_`开头的label, 插件会识别出这个pod是长期存在的pod, 改变jenkins slave的retentionStrategy改为always

如`label: "always-java always-centos"`

# Constraints
如果你定义了多个Container的话, 其中一个必须是Jenkins JNLP slave, 参数args必须是`${computer.jnlpmac} ${computer.name}`, 它将作为jenkins agent

其他的Container必须长期在前台运行, 不能运行一会就退出了. 如果image默认的entrypoint或者command仅仅运行了一会后就退出了, 那么你需要将它用长期运行的命令来替代它, 并且带上参数`ttyEnabled: true`

# Debugging
如果你想要明确知道插件和kubernetees api server交互的message的话, 你需要为`org.apache.http`定义一个新的[Jenkins log recorder](https://wiki.jenkins-ci.org/display/JENKINS/Logging), level是`DEBUG` 

# Building
run `mvn clean pacjages`
然后将生产的`target/kubernetes.hpi`插件上传到jenkins安装

# Docker image
可以在docker中运行jenkins, 基于官方[official image](https://hub.docker.com/_/jenkins/)

```sh
docker run --rm --name jenkins -p 8080:8080 -p 50000:50000 -v /var/jenkins_home csanchez/jenkins-kubernetes
```

# Running in Kubernetes
在本地可以使用[minikube](https://github.com/kubernetes/minikube)启动一个单节点的kubernetes集群

```sh
minikube start
```

然后创建 Jenkins ReplicationController and Service

```sh
kubectl create -f ./src/main/kubernetes/minikube.yml
kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin
```

# Config Cloud
`Manage Jenkins -> Configure System -> add a new cloud`

## kubernetes 1.4 warning
Until Kubernetes 1.4 removes the SNATing of source ips, seems that CSRF (enabled by default in Jenkins 2) needs to be configured to avoid `WARNING: No valid crumb was included in request errors`. This can be done checking Enable proxy compatibility under `Manage Jenkins -> Configure Global Security`

## Credentials
根据你的kubernetes集群配置为jenkins添加一个可用的Credentials, 下面这么几种可选
- Kubernetes Service Account
- Kubernetes api username and password
- OpenShift OAuth token (推荐)

## Other
Jenkins URL: 需要是以http开头的jenkins地址, 默认端口80

Jenkins Tunnel: 不以http开头的jenkins地址, 用于slave访问master, 默认端口50000

ContainerCap: k8s集群能够同时提供slave的个数

Kubernetes server certificate key	: X509 PEM encoded, 不能有换行, 不能有头尾, 就是一个字符串
