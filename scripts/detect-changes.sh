#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-}"
if [[ -z "$BASE_REF" && -n "${GIT_PREVIOUS_SUCCESSFUL_COMMIT:-}" ]]; then
  BASE_REF="$GIT_PREVIOUS_SUCCESSFUL_COMMIT"
elif [[ -z "$BASE_REF" && -n "${CHANGE_TARGET:-}" ]]; then
  BASE_REF="origin/${CHANGE_TARGET}"
elif [[ -z "$BASE_REF" ]]; then
  for candidate in origin/main origin/master; do
    if git rev-parse --verify "$candidate" >/dev/null 2>&1; then
      BASE_REF="$candidate"
      break
    fi
  done
fi

if [[ -z "$BASE_REF" ]] || ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  echo "Unable to resolve a comparison ref. Pass a base ref or fetch origin/main or origin/master." >&2
  exit 2
fi

changed_files="$(git diff --name-only "${BASE_REF}...HEAD")"
if [[ -z "$changed_files" ]]; then
  exit 0
fi

all_services=0
changed_services=()
for file in $changed_files; do
  case "$file" in
    services/pom.xml|services/Dockerfile|services/service-common/*|jenkins/*|Jenkinsfile*|scripts/*|helm/amazon-microservices/*|k8s/*|sonarqube/*|compose.yaml|.dockerignore|.gitignore)
      all_services=1
      ;;
    services/auth-service/*|helm/auth-service/*)
      changed_services+=(auth-service)
      ;;
    services/cart-service/*|helm/cart-service/*)
      changed_services+=(cart-service)
      ;;
    services/product-service/*|helm/product-service/*)
      changed_services+=(product-service)
      ;;
    services/user-service/*|helm/user-service/*)
      changed_services+=(user-service)
      ;;
  esac
done

if [[ "$all_services" -eq 1 ]]; then
  printf '%s\n' auth-service cart-service product-service user-service
  exit 0
fi

if [[ "${#changed_services[@]}" -gt 0 ]]; then
  printf '%s\n' "${changed_services[@]}" | sort -u
fi
