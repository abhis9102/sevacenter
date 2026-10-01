.PHONY: help db-up db-down db-logs run test build hooks
help: ## show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-10s %s\n",$$1,$$2}'
db-up: ## start local Postgres (needs .env)
	docker compose up -d db
db-down: ## stop local Postgres
	docker compose down
db-logs: ## tail Postgres logs
	docker compose logs -f db
run: ## run backend -> http://localhost:8080/api/v1/ping
	cd backend && ./mvnw spring-boot:run
test: ## hermetic tests (needs Docker)
	cd backend && ./mvnw -B -ntp verify
build: ## compile + package
	cd backend && ./mvnw -B -ntp package
hooks: ## install pre-commit hooks
	pre-commit install
