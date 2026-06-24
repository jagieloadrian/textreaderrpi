# TextReaderRpi Helm Chart

A Kubernetes Helm chart for deploying **TextReaderRpi** — a multi-zone display controller application — to a Kubernetes cluster.

## Prerequisites

- Kubernetes 1.19+ cluster
- Helm 3.0+
- Docker image `textreaderrpi:latest` (see [build instructions](../../README.md))
- Optional: Pi cluster for hardware access (`/dev/spidev0.0`, `/dev/i2c-1`)

## Installation

### Default Installation (Software-Only, OfflineDisplayDriver)

Deploy with no hardware access (application runs in OfflineDisplayDriver mode):

```bash
helm install textreaderrpi .
```

Wait for the pod to be ready:
```bash
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=textreaderrpi --timeout=60s
kubectl port-forward svc/textreaderrpi 8080:8080
```

Check health:
```bash
curl -sS http://127.0.0.1:8080/health
curl -sS http://127.0.0.1:8080/health/ready
```

### Installation with Hardware Access (Raspberry Pi Node)

To enable hardware access to GPIO/SPI devices, deploy on a Raspberry Pi node with the `hardwareAccess.enabled` toggle:

```bash
helm install textreaderrpi . \
  --set hardwareAccess.enabled=true \
  --set nodeSelector."kubernetes\.io/arch"=arm64
```

**Warning:** This requires:
- Pod to run on a Raspberry Pi node (use `nodeSelector` or `nodeAffinity`)
- Pi node must have `/dev/spidev0.0` and `/dev/i2c-1` devices exposed
- Pod will run in `privileged` mode to access device nodes
- Use only on secure, trusted clusters

**Verify hardware connection:**
```bash
kubectl logs -f deploy/textreaderrpi
```

Look for messages indicating hardware device detection and display updates.

### Installation with Custom Image Registry

If your image is in a private registry:

```bash
helm install textreaderrpi . \
  --set image.repository=myregistry.example.com/textreaderrpi \
  --set image.tag=v1.2.0 \
  --set image.pullPolicy=Always
```

### Installation with Ingress Enabled

Expose the application via Ingress (no TLS in this version):

```bash
helm install textreaderrpi . \
  --set ingress.enabled=true \
  --set ingress.host=textreader.local \
  --set ingress.className=traefik  # for K3s
```

Then access http://textreader.local/ (if you add the hostname to `/etc/hosts` or configure DNS).

### Installation with Custom Storage

Customize PVC sizes:

```bash
helm install textreaderrpi . \
  --set persistence.data.size=2Gi \
  --set persistence.logs.size=1Gi
```

## Configuration

Key configuration options in `values.yaml`:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `image.repository` | string | `textreaderrpi` | Container image repository |
| `image.tag` | string | `latest` | Image tag |
| `image.pullPolicy` | string | `Never` | Image pull policy (use `Always` for registries) |
| `hardwareAccess.enabled` | bool | `false` | Enable GPIO/SPI device access |
| `persistence.data.size` | string | `1Gi` | Size of H2 database volume |
| `persistence.logs.size` | string | `500Mi` | Size of logs volume |
| `service.port` | int | `8080` | Service port |
| `ingress.enabled` | bool | `false` | Enable Ingress |
| `ingress.host` | string | `""` | Ingress hostname |
| `rbac.create` | bool | `true` | Create ServiceAccount and RBAC resources |

For all parameters, see `values.yaml`.

## Uninstallation

```bash
helm uninstall textreaderrpi
```

Note: PersistentVolumeClaims are **not** automatically deleted. To remove data:
```bash
kubectl delete pvc textreaderrpi-data textreaderrpi-logs
```

## Deployment Scenarios

### K3s (on Raspberry Pi)

K3s with default `local-path` storage:

```bash
helm install textreaderrpi . \
  --set hardwareAccess.enabled=true \
  --set nodeSelector."kubernetes\.io/arch"=arm64 \
  --namespace textreader \
  --create-namespace
```

Check persistent storage:
```bash
kubectl get pv,pvc -n textreader
kubectl get nodes -L kubernetes.io/arch
```

### k3d (Docker-based K3s)

k3d with volume mounts:

```bash
# Note: hardware access is not available in k3d (no real GPIO/SPI devices in Docker)
helm install textreaderrpi . \
  --set hardwareAccess.enabled=false
```

### Multi-Node Kubernetes Cluster

If multiple nodes, use nodeSelector to target Pi nodes:

```bash
helm install textreaderrpi . \
  --set hardwareAccess.enabled=true \
  --set nodeSelector."node-type"=rpi-display \
  --set nodeSelector."kubernetes\.io/arch"=arm64
```

Label your Pi nodes beforehand:
```bash
kubectl label node <pi-node-name> node-type=rpi-display kubernetes.io/arch=arm64
```

## Health Checks

The application exposes two health endpoints:

- `GET /health` (liveness): basic application health
- `GET /health/ready` (readiness): database connectivity and readiness to accept requests

Kubernetes will use these for liveness and readiness probes automatically (configured in Deployment spec).

## Memory & Resource Constraints

The JVM heap is capped at **220 MB** to stay within the Raspberry Pi's typical 256 MB memory limit:

```
JAVA_TOOL_OPTIONS: "-Xmx220m"
```

If you're running on a larger node with more memory, this can be overridden:

```bash
helm install textreaderrpi . \
  --set JAVA_TOOL_OPTIONS="-Xmx512m"  # For nodes with more RAM
```

## Troubleshooting

### Pod stuck in `Pending` state

Check PVC binding:
```bash
kubectl get pvc
kubectl describe pvc textreaderrpi-data
```

Ensure your cluster has a storage class and provisioner (K3s uses `local-path` by default).

### Pod crashes with `ImagePullBackOff`

Image not found on local Docker daemon. With `imagePullPolicy: Never`, you must pre-load the image:

```bash
# On the target node, load the image:
docker load < textreaderrpi-latest.tar

# Or override to pull from registry:
helm install textreaderrpi . \
  --set image.repository=myregistry.example.com/textreaderrpi \
  --set image.pullPolicy=Always
```

### Hardware access fails (no `/dev/spidev0.0`)

Ensure:
1. Pod is running on a Raspberry Pi node with SPI enabled
2. Pod has `privileged: true` in securityContext (verify via `kubectl get pod -o yaml`)
3. Node has `/dev/spidev0.0` exposed (check node's `/dev/` directory)

If not a Raspberry Pi, disable hardware:
```bash
helm install textreaderrpi . --set hardwareAccess.enabled=false
```

### Pod runs but app logs don't appear

Check pod logs:
```bash
kubectl logs -f deploy/textreaderrpi
kubectl logs -f deploy/textreaderrpi -c textreaderrpi --tail=100
```

Check events:
```bash
kubectl describe pod <pod-name>
```

### Database locked or connection refused

If upgrading or restarting pods frequently, H2 database may have stale locks. Delete the PVC and PV:

```bash
kubectl delete pvc textreaderrpi-data --grace-period=0 --force
```

The pod will recreate the PVC and initialize a fresh database. **Note:** this deletes all history data.

## Helm Chart Details

- **Replicas**: Hardcoded to **1** (no scaling). Pi hardware (GPIO, SPI, file-based database) cannot be shared across pods.
- **Storage**: Two PersistentVolumeClaims (data, logs). Defaults to local-path provisioner (K3s).
- **RBAC**: ServiceAccount + minimal Role (pods get/list permission) for self-discovery.
- **Image Pull**: Default policy is `Never` (assumes pre-loaded image). Override for registry deployments.
- **TLS**: Not included (deferred to v1.3+). For production, use a reverse proxy or service mesh.

## References

- [TextReaderRpi GitHub](https://github.com/yourusername/textreaderrpi)
- [Kubernetes Helm Documentation](https://helm.sh/docs/)
- [K3s Documentation](https://docs.k3s.io/)
- [Raspberry Pi & Kubernetes](https://www.raspberrypi.org/documentation/)

## License

See [LICENSE](../../LICENSE) in the repository root.

