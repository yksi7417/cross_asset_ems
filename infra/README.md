# Infrastructure

Deployment, observability, and ops infrastructure as code.

| Folder | Purpose |
|---|---|
| `docker-compose/` | Local dev stack — Aeron cluster, Postgres, OpenSearch, Prometheus, Grafana, OTel collector, Jaeger. |
| `k8s/` | Production Kubernetes manifests per pod (Tier-1 dedicated, Tier-2/3 shared). |
| `terraform/` | AWS infra: VPC, placement groups, c-series compute, i-series storage per [[arch-deployment]]. |
| `grafana/` | Dashboards: golden signals, per-asset latency, per-component cluster health, business-level views. |
| `prometheus/` | Scrape configs + alerting rules. |
| `otel-collector/` | OpenTelemetry collector config: receivers, processors, exporters to OpenSearch + Jaeger + Prometheus. |
| `opensearch/` | Index templates, ILM policies for log retention per [[arch-jurisdictional-compliance]]. |

## Tiered environments

Per [[arch-deployment]]:

- **Dev** — `docker-compose/` on a developer box.
- **QA** — GitHub Actions ephemeral runners.
- **UAT-1 / UAT-2** — smaller AWS, blue/green pair.
- **PROD-1 / PROD-2** — Aeron-tuned AWS per the Aeron AWS performance testing guide.

All four environments run the **same OCI images**; only config differs.

## See also

- [[arch-deployment]] (the deployment architecture)
- [[arch-resilience-24x7]] (continuity model)
- [[arch-observability]] (the three pillars stack)
- [[arch-configuration-service]] (where runtime config lives)
