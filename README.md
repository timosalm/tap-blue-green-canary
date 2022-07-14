# Advanced Deployment Strategies (Blue/Green, Canary) for TAP

As long as TAP doesn't offer first-class blue/green, canary deployment support OOTB, this repository aims to show how it can be implemented with common tools used for it.

To see the advanced deployment strategies in action, you have to **fork the repository or use another sample application**.

## Knative (part of TAP)

### Prerequisites 
- TAP installation with version >= 1.1 and GitOps configured (can be also done via Workload)
- Set environment variable for your configured dev namespace
  ```
  export DEVELOPER_NAMESPACE=default 
  ```
- [kn CLI](https://knative.dev/docs/client/install-kn/)

### Apply the custom supply chain
```
kubectl apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/kubernetes-actions/0.2/kubernetes-actions.yaml -n $DEVELOPER_NAMESPACE
kubectl apply -f knative/supply-chain -n $DEVELOPER_NAMESPACE
```

### Blue/Green deployment
1. Change the knative/workload-blue-green.yaml according the location of your sample application code location and maybe GitOps configuration.
2. Apply the workload to deploy the first version of your application
    ```
    kubectl apply -f knative/workload-blue-green.yaml
    ```
3. Verify that you can view your application. Reuse the same command to verify the other steps!
    ```
    curl -L $(kn service describe blue-green-knative -n $DEVELOPER_NAMESPACE -o url)
    ```
4. Deploy a second version of your application by modifying the source code of your sample and pushing it to remote. For the sample provided with this repository, you can change the output String in `sample-app/src/main/java/com/example/helloworld/HelloWorldResource.java` e.g. from `Hello Blue!` to `Hello Green!` or vice versa
5. After the second version is deployed still all traffic is being sent to the first version.
6. Let's now update our Knative Service so that 50% of traffic is being sent to the first version, and 50% is being sent to the second version via editing the `config/$DEVELOPER_NAMESPACE/blue-green-knative/delivery.yaml` in your GitOps repository and pushing it to remote. See an example here:
    ```
      traffic:
      - latestRevision: true
        percent: 50
        revisionName: blue-green-knative-f65eefa4-7f0b-4c34-90e8-c5b19d403508
      - latestRevision: false
        percent: 50
        revisionName: blue-green-knative-14feb3f5-7ee2-4640-96dc-471d74af7ddf
    ```
7. Once you are ready to route all traffic to the new version of the app, update the Knative Service again to send 100% of traffic to the second Revision:
   ```
      traffic:
      - latestRevision: true
        percent: 100
        revisionName: blue-green-knative-f65eefa4-7f0b-4c34-90e8-c5b19d403508
      - latestRevision: false
        percent: 0
        revisionName: blue-green-knative-14feb3f5-7ee2-4640-96dc-471d74af7ddf
    ```
    ```
    curl -L $(kn service describe blue-green-knative -n $DEVELOPER_NAMESPACE -o url)
    ```
    Hint: You can remove the first version instead of setting it to 0% of traffic if you do not plan to roll back. Non-routeable versions are then garbage-collected.
8. If you will deploy new versions of you app 100% of the traffic will go to the previous version and you can follow the same procedure from step 6 for the blue/green deployment. Don't forget to pull the latest state of the GtiOps repository before you apply any changes!


## ArgoCD (WIP)

### Prerequisites 
- TAP installation with version >= 1.1
- ArgoCD installed in the run cluster
- [Argo Rollouts installed](https://argoproj.github.io/argo-rollouts/installation/) in the run cluster
- Set environment variable for your configured dev namespace
  ```
  export DEVELOPER_NAMESPACE=default 
  ```
### Apply the custom supply chain
```
kubectl apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/kubernetes-actions/0.2/kubernetes-actions.yaml -n $DEVELOPER_NAMESPACE
kubectl apply -f knative/supply-chain -n $DEVELOPER_NAMESPACE
```

## Flagger (WIP) 