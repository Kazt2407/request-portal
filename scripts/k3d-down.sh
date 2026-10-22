#!/usr/bin/env bash
# Deletes the local k3d cluster. The docker compose infrastructure and its data are not touched.
set -euo pipefail
k3d cluster delete request-portal
