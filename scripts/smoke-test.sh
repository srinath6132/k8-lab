#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://shop.local:8080}"
BASE_URL="${BASE_URL%/}"

echo "Checking storefront at ${BASE_URL}/"
curl --fail --silent --show-error --output /dev/null \
  --header 'Host: shop.local' \
  --write-out 'storefront HTTP %{http_code}\n' "${BASE_URL}/"

echo "Checking product API at ${BASE_URL}/api/products"
products="$(curl --fail --silent --show-error \
  --header 'Host: shop.local' "${BASE_URL}/api/products")"
if ! grep -q '"rice-001"' <<< "$products"; then
  echo "Product API response did not contain the expected rice-001 catalog item" >&2
  exit 1
fi
echo "product API returned the kirana catalog"
