# Advanced Deployment Strategies (Blue/Green, Canary) for TAP

As long as TAP doesn't offer first-class blue/green, canary deployment support OOTB, this repository aims to show how it can be implemented with common tools used for it.

[PPT Slide (VMware Internal)](https://via.vmw.com/EkMJ)

To see the advanced deployment strategies in action, you have to **fork the repository or use another sample application**.

A **canary** rollout is a deployment strategy where the operator releases a new version of their application to a small percentage of the production traffic.
A **Blue-green** deployment is a deployment strategy where two versions of an application or service are deployed ("blue" (aka staging) and a "green" (aka production)) to do quality assurance and user acceptance testing of a new version. User traffic is shifted from the green to the blue version once new changes have been tested and accepted.

## Knative (part of TAP)

### Limitations
- **Doesn't work with Service-Bindings** or other modifications to the Knative Serving Service that happen in the cluster due to the deterministic naming of Revisions used for multi-cluster support.

### Setup 
```
kubectl apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/kubernetes-actions/0.2/kubernetes-actions.yaml -n $DEVELOPER_NAMESPACE
```

Customize a OOTB supply chain you exported from the cluster by changing the name, adding unique selectors and removing all generated fields (like annotations, status, ...).

Add the `revision-config-provider` resource before the `app-config` resource for which you have to add the output of the `revision-config-provider` to the inputs and change the `templateRef`to `config-template-knative`.
```
  - configs:
    - name: config
      resource: config-provider
    name: revision-config-provider
    templateRef:
      kind: ClusterConfigTemplate
      name: revision-config-template-knative
  - configs:
    - name: revisionconfig
      resource: revision-config-provider
    - name: specconfig
      resource: config-provider 
    name: app-config
    templateRef:
      kind: ClusterConfigTemplate
      name: config-template-knative
```

Apply the custom templates to the cluster.
```
kubectl apply -f knative/
```

Create and apply a Workload with the following `.spec.source` configuration if you forked the repository and the labels you chose for the supplyc chain.
```
  source:
    git:
      url: https://github.com/<your-user>/tap-blue-green-canary.git
      ref:
        branch: main
    subPath: sample-app/
```

For both advanced deployment strategies it's **recommended to use GitOps** to shift the traffic between the deployed versions by editing the configuration in the repository.
If you're using RegistryOps ot want to use e.g. the kn CLI to do it directly at the deployed resource in the cluster, you have to customize the OOTB ClusterDelivery and ClusterDeploymentTemplate via `rebaseRules` for the `App` resource because kapp-controller will otherwise revert your changes.

### Canary deployment
1. Change something in your sample application after the first deployment to deploy a new version.
2. Get the url via TAP GUI, `tanzu apps workload get <workload_name> -n <developer_namespace>` or
  ```
  kubectl get kservice <workload_name> -n <developer_namespace> -o jsonpath='{.status.url}'
  ```
3. Shift traffic step by step to the newet revision by editing the Knative Serving Service resource in e.g. the GitOps repository.
  ```
  spec:
    traffic:
    - latestRevision: true
      tag: canary
    - revisionName: <new-revision-name>
      percent: 0
      tag: blue
    - revisionName: <previous-revision-name>
      percent: 100
      tag: green
  ```
  to
  ```
  spec:
    traffic:
    - latestRevision: true
      tag: canary
    - revisionName: <new-revision-name>
      percent: 25
      tag: blue
    - revisionName: <previous-revision-name>
      percent: 75
      tag: green
  ```

### Blue/Green deployment
1. Change something in your sample application after the first deployment to deploy the new, blue version.
2. Do quality assurance and user acceptance testing with the blue version. You can get the url via the following command:
  ```
  kubectl get kservice <workload_name> -n <developer_namespace> -o jsonpath='{.status.traffic[?(@.tag=="blue")].url}'
  ```
3. Shift traffic to the blue version by editing the Knative Serving Service resource in e.g. the GitOps repository.
  ```
  spec:
    traffic:
    - latestRevision: true
      tag: canary
    - revisionName: <new-revision-name>
      percent: 0
      tag: blue
    - revisionName: <previous-revision-name>
      percent: 100
      tag: green
  ```
  to
  ```
  spec:
    traffic:
    - latestRevision: true
      tag: canary
    - revisionName: <new-revision-name>
      percent: 100
      tag: blue
    - revisionName: <previous-revision-name>
      percent: 0
      tag: green
  ```

## ArgoCD

### Limitations
- Doesn't work with Knative Serving (Horizontal Pod Autoscaling is supported by ArgoCD Rollouts)
- Automated ingress creation for multi cluster setup is out of scope of this example and only possible with additional customizations in the ClusterDelivery
- Doesn't work for 

### Setup 

Install ArgoCD and [Argo Rollouts](https://argoproj.github.io/argo-rollouts/installation/) in the run cluster.

Customize a OOTB supply chain you exported from the cluster by changing the name, adding unique selectors and removing all generated fields (like annotations, status, ...).

```
ytt -f argocd/supply-chain/supply-chain.yaml -f argocd/supply-chain/rbac.yaml -f argocd/supply-chain/config-template-multi-cluster.yaml -v gitops_repository_owner=tsalm -v container_registry.repository=tap-wkld -v container_registry.hostname=registry.example.com -v ingress.domain="" -v developer_namespace=default
```

#### Single cluster setup
Change the `templateRef`to `config-template-argo` for the `app-config` resource.
```
  - configs:
    - name: config
      resource: config-provider
    name: app-config
    templateRef:
      kind: ClusterConfigTemplate
      name: config-template-argo
    params:
    - name: ingress_domain
      value: <tap-ingress-domain>
```

Apply the related custom template to the cluster.
```
kubectl apply -f argocd/config-template.yaml
```

Apply the RBAC configuration to the developer namespace the Workload for the sample application will be applied to.
```
ytt -f argocd/rbac.yaml -v developer_namespace=<dev-ns> | kubectl apply -f -
```

#### Multi cluster setup (doesn't include ingress creation)
Change the `templateRef`to `config-template-argo` for the `app-config` resource.
```
  - configs:
    - name: config
      resource: config-provider
    name: app-config
    templateRef:
      kind: ClusterConfigTemplate
      name: config-template-argo
```

Apply the related custom template to the cluster.
```
kubectl apply -f argocd/config-template-multi-cluster.yaml
```

Apply the RBAC configuration to the developer namespace of the **run cluster** the sample application will be applied to.
```
ytt -f argocd/rbac.yaml -v developer_namespace=<dev-ns> | kubectl apply -f -
```

For both setups create a Workload with the following `.spec.source` configuration if you forked the repository and the labels you chose for the supplyc chain.
```
  source:
    git:
      url: https://github.com/<your-user>/tap-blue-green-canary.git
      ref:
        branch: main
    subPath: sample-app/
```

## Canary deployment
Add / update the following parameter to / in your Workload and apply it.
```
spec:
  params:
  - name: deployment_strategy
    value: canary
```

1. Change something in your sample application after the first deployment to deploy a new version.
2. Get the url of the application. 
  - For the **single cluster** setup you can run the following command.
    ```
    kubectl get httpproxy <workload_name> -n <developer_namespace>  -o jsonpath='{.spec.virtualhost.fqdn}'
    ```
  - For the **multi cluster** setup you can manually create an ingress object on it or expose the Service to your local machine via the following command, which makes the application available at http://localhost
    ```
    kubectl port-forward service/<workload_name> -n <developer_namespace> 80:8080
    ```
3. Call the url several times to see how the traffic will be automatically shifted step-by-step and/or run
   ```
   watch "kubectl get replicaset -n dev | grep argo"
   ```

### Blue/Green deployment (WIP)
Add / update the following parameter to / in your Workload and apply it.
```
spec:
  params:
  - name: deployment_strategy
    value: blue-green
```

1. Change something in your sample application after the first deployment to deploy athe new, blue version.
2. Do quality assurance and user acceptance testing with the blue version. You can get the url via the following command:
   - For the **single cluster** setup you can run the following command.
    ```
    kubectl get httpproxy <workload_name>-blue -n <developer_namespace>  -o jsonpath='{.spec.virtualhost.fqdn}'
    ```
  - For the **multi cluster** setup you can manually create an ingress object on it or expose the Service to your local machine via the following command, which makes the application available at http://localhost
    ```
    kubectl port-forward service/<workload_name>-blue -n <developer_namespace> 80:8080
    ```
3. Shift traffic to the blue version by running the following command with the kubectl argo plugin.
  ```
  kubectl argo rollouts promote <workload_name> -n <developer_namespace>
  ```

## Flagger (WIP) 
```
helm repo add flagger https://flagger.app
helm upgrade -i flagger flagger/flagger \
--namespace tanzu-system-ingress \
--set meshProvider=contour \
--set ingressClass=contour \
--set prometheus.install=true
```

## Other options
- [Contour](https://projectcontour.io/docs/v1.21.1/config/request-routing/#upstream-weighting) (Ingress Controller) 
- [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#the-weight-route-predicate-factory) / VMware Spring Cloud Gateway for Kubernetes

