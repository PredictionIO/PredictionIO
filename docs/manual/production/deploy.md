---
layout: docs
title: Production Deployment
---

# Production Deployment
(coming soon)

## Scalability 

* Launch more than 1 Event Server to handle more load. For instance you can launch 10 Event Server to sink to a single, large HBase cluster.

* Launch multiple "Engine Instances" for the purpose of load balancing, and the SDK's EngineClient may points to a load-balancer instead.