.PHONY: help db-up db-down db-logs run test build hooks dast
help: ## show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-10s %s\n",$$1,$$2}'
db-up: ## start local Postgres (needs .env)
	docker compose up -d db
db-down: ## stop local Postgres
	docker compose down
db-reset: ## wipe + recreate Postgres (re-runs db-init, drops all data)
	docker compose down -v && docker compose up -d db
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
	python3 tools/security/dast_policy.py gate .dast/zap.json --out .dast/findings.json
