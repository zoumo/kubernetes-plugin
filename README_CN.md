# Jenkins-kubernetes-plugin

这个jenkins插件在默认情况下创建一个 Kubernetes Pod 作为 jenkins 的 slave , 在 pod 中运行定义好的 docker image 来进行一次构建任务, 在构建完了之后将 pod 停止后删除
这个 repo 对插件进行了改进, 使得插件能够创建长期存在的pod.

slaves 使用 JNLP 来自动连接 jenkins master, 所以下列的环境变量会自动添加到每一个 Container 中:
* `JENKINS_URL`: Jenkins web interface url
* `JENKINS_JNLP_URL`: url for the jnlp definition of the specific slave
* `JENKINS_SECRET`: the secret key for authentication
* `JENKINS_NAME`: the name of the Jenkins agent

需要配合 [jenkinsci/jnlp-slave](https://hub.docker.com/r/jenkinsci/jnlp-slave/) 使用, 源码 [Dockerfile](https://github.com/jenkinsci/docker-jnlp-slave)

# Pipeline support
Nodes 可以在 pipeline 中定义并且使用

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
    node ('mypod') { // 与上面的 label 一致
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
只需要在 podTemplate 的中指定一个以 `always-` 或者 `always_` 开头的 label, 插件会识别出这个 pod 是长期存在的 pod, 改变 jenkins slave 的 retentionStrategy 改为 always

如`label: "always-java always-centos"`

# Constraints
如果你定义了多个 Container 的话, 其中一个必须是 Jenkins JNLP slave, 参数 args 必须是 `${computer.jnlpmac} ${computer.name}`, 它将作为 jenkins agent

其他的 Container 必须长期在前台运行, 不能运行一会就退出了. 如果 image 默认的 entrypoint 或者 command 仅仅运行了一会后就退出了, 那么你需要将它用长期运行的命令来替代它, 并且带上参数 `ttyEnabled: true`

# Debugging
如果你想要明确知道插件和 kubernetees api server 交互的 message 的话, 你需要为 `org.apache.http` 定义一个新的 [Jenkins log recorder](https://wiki.jenkins-ci.org/display/JENKINS/Logging), level 是 `DEBUG` 

# Building
run `mvn clean pacjages`
然后将生产的 `target/kubernetes.hpi` 插件上传到 jenkins 安装

# Jenkins Master with plugin

### Docker image

#### Build

```
docker build -t "jenkins-k8s" .
```

#### Running

可以在 docker 中运行 jenkins with plugin, 下面这个image已经安装了插件 基于官方 [official image](https://hub.docker.com/_/jenkins/)

```sh
mkdir jenkins_home
docker run --rm --name jenkins -p 8080:8080 -p 50000:50000 -v $PWD/jenkins_home:/var/jenkins_home cargo.caicloud.io/circle/jenkins-k8s:2.19.4-alpine
```

### Running in Minikube

在本地可以使用 [minikube](https://github.com/kubernetes/minikube) 启动一个单节点的 kubernetes 集群

```sh
minikube start
```

>   // minikube 有个小问题, PersistentVolume 中使用 hostPath 创建的目录权限是0755, 这使得 docker 没有权限操作这个目录
>   // 需要手动修改目录的权限
>
>   ```
>   minikube ssh
>
>   // in minikube vm
>
>   sudo mkdir -m 0777 -p /data/kubernetes-plugin-jenkins
>   ```

然后创建 Jenkins ReplicationController and Service

```sh
kubectl create -f ./src/main/kubernetes/minikube.yml
kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin

// 映射jenkins master的service到本机
minikube service jenkins --namespace=kubernetes-plugin
```

### Running in kubernetes

如果你有自己的kubernetes集群的话, 则需要修改 `./src/main/kubernetes/minikube.yml` , 将其中 `PersistentVolume` 中的 `hostPath` 改成集群中实际使用的方式如 `nfs`, 然后再

```
kubectl create -f ./src/main/kubernetes/minikube.yml
kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin
```

## Jenkins jnlp slave

```
docker buil	-t "jnlp-slave" ./src/main/jnlp-slave
```



# Config Cloud

`Manage Jenkins -> Configure System -> add a new cloud`

## kubernetes 1.4 warning
Until Kubernetes 1.4 removes the SNATing of source ips, seems that CSRF (enabled by default in Jenkins 2) needs to be configured to avoid `WARNING: No valid crumb was included in request errors`. This can be done checking Enable proxy compatibility under `Manage Jenkins -> Configure Global Security`

## Credentials
根据你的 kubernetes 集群配置为jenkins添加一个可用的Credentials, 下面这么几种可选
- Kubernetes Service Account
- Kubernetes api username and password
- OpenShift OAuth token (推荐)

## NodeProvisioner Strategy

Jenkins 默认使用 hudson.slaves.NodeProvisioner.StandardStrategyImpl, 作为 Node 创建的调度策略

这个策略会经过一系列复杂的统计和计算来确定是否要新建节点 [Why?](https://support.cloudbees.com/hc/en-us/articles/204690520-Why-do-slaves-show-as-suspended-while-jobs-wait-in-the-queue-), 如果你不需要这么复杂的策略, 可以

勾选下面这个策略, 它提供了一个 asap provision 的策略来提供 node

`Manage Jenkins -> Configure System -> cloud -> Use kubernetes provisioning strategy	`

## Other
Jenkins URL: 需要是以 http 开头的 jenkins 地址, 默认端口80

Jenkins Tunnel: 不以 http 开头的 jenkins 地址, 用于 slave 访问 master, 默认端口50000

ContainerCap: k8s 集群能够同时提供 slave 的个数

Kubernetes server certificate key	: X509 PEM encoded, 不能有换行, 不能有头尾, 就是一个字符串
