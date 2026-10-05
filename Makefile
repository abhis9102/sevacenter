.PHONY: help up down db-up db-down db-logs run test build hooks dast image-scan fe-dev fe-check
help: ## show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-10s %s\n",$$1,$$2}'
db-up: ## start local Postgres + Mailpit (needs .env; mail UI http://localhost:8025)
	docker compose up -d db mail
up: ## the whole stack from container images, hardened like production (FRONTEND_PORT in .env if 3000 is taken)
	docker compose --profile app up -d --build
	@echo "staff app: http://<slug>.localhost:$${FRONTEND_PORT:-3000}   mail: http://localhost:8025"
down: ## stop the whole stack (data kept)
	docker compose --profile app down
db-down: ## stop local Postgres
	docker compose down
db-reset: ## wipe + recreate Postgres (re-runs db-init, drops all data)
	docker compose down -v && docker compose up -d db mail
db-logs: ## tail Postgres logs
	docker compose logs -f db
run: ## run backend (local profile, 2-role DB) -> http://localhost:8080/api/v1/ping
	@test -f .env || { echo 'missing .env — cp .env.example .env and set passwords'; exit 1; }
	set -a && . ./.env && set +a && cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
test: ## hermetic tests (needs Docker)
	cd backend && ./mvnw -B -ntp verify
build: ## compile + package
	cd backend && ./mvnw -B -ntp package
hooks: ## install pre-commit hooks
	pre-commit install
dast: ## DAST: build the jar, ZAP-scan it against a throwaway DB, apply the policy gate (report: .dast/zap.html)
	cd backend && ./mvnw -B -ntp -q package -DskipTests
	tools/security/dast.sh backend/target/backend-0.0.1-SNAPSHOT.jar .dast
	python3 tools/security/dast_policy.py gate .dast/zap-authed.json .dast/zap.json --out .dast/findings.json
image-scan: ## G6: build the backend image, Trivy-scan it and its Dockerfile, apply the gate (report: .image-scan/)
	tools/security/image_scan.sh .image-scan
fe-dev: ## run the staff admin web app -> http://<slug>.localhost:3000 (backend on :8080)
	cd frontend && npm ci && npm run dev
fe-check: ## frontend lint + typecheck + unit tests + production build
	cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build
