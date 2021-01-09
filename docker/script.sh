#!/bin/bash
#
docker-compose -f docker-compose.yml \
    -f pgsql/docker-compose.base.yml \
    -f pgsql/docker-compose.meta.yml \
    -f pgsql/docker-compose.event.yml \
    -f pgsql/docker-compose.model.yml \
    up

#
docker-compose -f docker-compose.yml \
    -f pgsql/docker-compose.base.yml \
    -f pgsql/docker-compose.meta.yml \
    -f pgsql/docker-compose.event.yml \
    -f pgsql/docker-compose.model.yml \
    down

#
docker-compose -f docker-compose.yml \
    -f elasticsearch/docker-compose.base.yml \
    -f elasticsearch/docker-compose.meta.yml \
    -f elasticsearch/docker-compose.event.yml \
    -f pgsql/docker-compose.model.yml \
    up