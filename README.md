## SPRINGBOOT MANIFEST MAVEN PLUGIN

### Maven Plugin to generate the following manifest files.
* Kubernetes Deployment files and Config Map files pairs for every spring profile (application-<XXXX>.properties)
* Git version tree with the maven version in a JSON and HTML so it can be packaged in the jar file as version metadata.

### Kubernetes Manifests.
Imagine we have the following application properties files in a regular spring boot app.
```
 src
   main
     resources
        application.properties
        application-dev.properties
        application-qa.properties
        application-stage.properties
        application-prod.properties
```

The springboot application would then need to supply a deployment.yml and configmap.yml template with variables:

### Deployment.yml template example (note that all the variables defined in the application.properties can be accessed here)
In addition to that there are implicit variables: such as `artifactId, version, gitVersion, configMapTemplateName, imageName, replicas, minReadySeconds` available for use.
```
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  labels:
    app: ${artifactId}
  name: ${artifactId}
  namespace: default
spec:
  replicas: ${replicas}
  minReadySeconds: ${minReadySeconds}
  selector:
    matchLabels:
      app: ${artifactId}
  strategy:
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 25%
    type: RollingUpdate
  template:
    metadata:
      labels:
        app: ${artifactId}
    spec:
      containers:
        - name: ${artifactId}
          image: ${imageName}
          imagePullPolicy: Always
          ports:
            - name: liveness-port
              containerPort: ${server.port}
          resources:
            requests:
              cpu: 100m
              memory: 512Mi
            limits:
              cpu: 500m
              memory: 1024Mi
          readinessProbe:
            httpGet:
              path: /
              port: liveness-port
            failureThreshold: 5
            periodSeconds: 10
            initialDelaySeconds: 60
          livenessProbe:
            httpGet:
              path: /
              port: liveness-port
            failureThreshold: 5
            periodSeconds: 10
            initialDelaySeconds: 60
      restartPolicy: Always
      terminationGracePeriodSeconds: 30
---
apiVersion: v1
kind: Service
metadata:
  labels:
    app: ${artifactId}
  name: ${artifactId}
  namespace: default
spec:
  type: NodePort
  ports:
    - port: ${server.port}
      protocol: TCP
      name: http
  selector:
    app: ${artifactId}
  sessionAffinity: None
```

### ConfigMap.yml template example (note that all the variables defined in the application.properties can be accessed here)
```
apiVersion: v1
kind: ConfigMap
metadata:
  name: ${configMapTemplateName}
  namespace: default
data:
${properties}
```
Note that `${properties}` will be replaced with all the properties resolved from the application*.properties per environment.

### The values of the variables are resolved in the same way as Spring would resolve it (application.properties --> application-<xxx>.properties).

### Output produced.
The above example would produce 5 pairs of Kubernetes manifest files (each pair per environment).
* deployment.yml, configMap.yml - **base** 
* deployment_dev.yml, configMap_dev.yml - **dev** 
* deployment_qa.yml, configMap_qa.yml - **qa** 
* deployment_stage.yml, configMap_stage.yml - **stage** 
* deployment_prod.yml, configMap_prod.yml - **prod**

## Usage for Kubernetes Manifests generation:
```
<plugin>
    <groupId>com.github.saiprasadkrishnamurthy</groupId>
    <artifactId>springboot-manifest-maven-plugin</artifactId>
    <version>1.1</version>
    <executions>
        <execution>
            <id>generate-k8s-manifests</id>
            <goals>
                <goal>generate-k8s-manifests</goal>
            </goals>
        <configuration>
            <dockerImageNamespace>saiprasadkrishnamurthy</dockerImageNamespace>
            <deploymentYmlTemplateFile>deployment/deployment.yml</deploymentYmlTemplateFile>
            <configMapYmlTemplateFile>deployment/configMap.yml</configMapYmlTemplateFile>
            <skip>false</skip> <!-- Optional default false -->
            <outputDir>target/manifests/k8s</outputDir> <!-- Optional, defaults to target/manifests/k8s -->
        </configuration>
        </execution>
    </executions>
</plugin>
```

## Usage for GIT Manifests generation:
```
<plugin>
    <groupId>com.github.saiprasadkrishnamurthy</groupId>
    <artifactId>springboot-manifest-maven-plugin</artifactId>
    <version>1.1</version>
    <executions>
        <execution>
            <id>generate-git-manifests</id>
            <goals>
                <goal>generate-git-manifests</goal>
            </goals>
        <configuration>
            <ticketPatterns>SPR-*,ABC-*</ticketPatterns> <!-- Optional: A comma separated list of Regex of the issue ticket ids in your issue tracking system --> 
            <skip>false</skip> <!-- Optional default false-->
            <outputDir>target/manifests/git</outputDir> <!-- Optional, defaults to target/manifests/git -->
        </configuration>
        </execution>
    </executions>
</plugin>
```

### The generated GIT Manifests HTML will look like this (which can be bundled into any springboot JAR file).
<img src="html.png" width=800 height=600 />

 
