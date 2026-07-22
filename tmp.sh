echo "===== package.json ====="
cat package.json

echo -e "\n===== pnpm-workspace.yaml ====="
cat pnpm-workspace.yaml

echo -e "\n===== .npmrc ====="
cat .npmrc 2>/dev/null || echo "(No .npmrc found)"

echo -e "\n===== Dockerfile ====="
cat Dockerfile

echo -e "\n===== .dockerignore ====="
cat .dockerignore 2>/dev/null || echo "(No .dockerignore found)"
