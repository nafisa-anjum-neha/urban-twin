# UrbanTwin on Kubernetes

Flat manifests that stand up the whole stack (contract §10):

| File | What it creates |
|---|---|
| `00-namespace.yaml` | `urbantwin` namespace |
| `01-config.yaml` | `urbantwin-db` Secret + `urbantwin` ConfigMap (env for all services) |
| `02-postgres-statefulset.yaml` | PostGIS 3.4 StatefulSet + headless Service + 5 Gi PVC |
| `03-redis.yaml` | Redis 7 Deployment + Service |
| `04-kafka.yaml` | Single-node Kafka (KRaft, no ZooKeeper) Deployment + Service |
| `05-api.yaml` | Spring Boot API Deployment + Service (wait-for-postgres init container) |
| `06-ml.yaml` | FastAPI ML service Deployment + Service |
| `07-web.yaml` | nginx-served React SPA Deployment + NodePort Service (`:30300`) |
| `08-ingress.yaml` | Optional nginx Ingress (`urbantwin.local` → web/api/ml) |

## Quickstart

Build and load the three app images first (names/versions must match the manifests):

```bash
docker build -t urbantwin/api:1.0.0 backend
docker build -t urbantwin/ml:1.0.0 ml-service
docker build -t urbantwin/web:1.0.0 frontend
```

Load them into a local cluster if needed:

```bash
kind load docker-image urbantwin/api:1.0.0 urbantwin/ml:1.0.0 urbantwin/web:1.0.0
# or, for minikube:
minikube image load urbantwin/api:1.0.0 urbantwin/ml:1.0.0 urbantwin/web:1.0.0
```

Then apply everything in order with one command:

```bash
kubectl apply -f deploy/k8s
```

Watch the stack come up:

```bash
kubectl -n urbantwin get pods -w
```

## Accessing the demo

* **NodePort** — `http://<node>:30300` (e.g. `localhost:30300` with kind's extraPortMappings or minikube). The nginx image inside `web` proxies `/api/*` to the API and `/ml/*` to the ML service, so the SPA works without the Ingress.
* **Ingress (optional)** — after installing an nginx ingress controller, add `urbantwin.local` to `/etc/hosts` and browse `http://urbantwin.local`.
* **Port-forward** — `kubectl -n urbantwin port-forward svc/web 30300:80`.

## Notes

* `APP_SEED_ENABLED=true` makes the API seed Dhaka demo data into the fresh database on first boot; on subsequent restarts seeding is skipped automatically (entities already exist).
* Kafka runs in KRaft mode (no ZooKeeper) with replication factor 1 — appropriate for a demo; not for production.
* The API degrades gracefully if redis or kafka are slow to start: REST, STOMP and SSE paths are resilient, and the ML client falls back to built-in heuristics.
