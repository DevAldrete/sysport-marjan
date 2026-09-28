SHELL := /bin/bash
MVN ?= mvn
NPM ?= npm
COMPOSE ?= docker compose

.DEFAULT_GOAL := help

.PHONY: help setup db-up db-down db-reset api api-build web test test-it build up down docs clean

help: ## Show this help
	@echo "SysPort MARJAN - common tasks"
	@echo
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## Create .env (if missing) and install frontend dependencies
	@test -f .env || (cp .env.example .env && echo "created .env from .env.example")
	$(NPM) --prefix frontend install

db-up: ## Start the MySQL container
	$(COMPOSE) up -d mysql

db-down: ## Stop the containers (data kept)
	$(COMPOSE) down

db-reset: ## Wipe the database volume and start fresh (re-runs db/init)
	$(COMPOSE) down -v
	$(COMPOSE) up -d mysql

api-build: ## Build the API executable jar
	$(MVN) -q -pl api -am -DskipTests package

api: api-build ## Build and run the API on :8080
	java -jar api/target/sysport-api-1.0-SNAPSHOT.jar

web: ## Run the Vue dev server on :5173 (proxies /api to :8080)
	$(NPM) --prefix frontend run dev

test: ## Run the fast unit tests (no database)
	$(MVN) test

test-it: ## Run the integration tests against a freshly seeded database
	SYSPORT_IT=1 $(MVN) test

build: ## Build backend and frontend for production
	$(MVN) -q -DskipTests package
	$(NPM) --prefix frontend run build

up: ## Build and start mysql + api + web with Docker Compose
	$(COMPOSE) up -d --build

down: ## Stop the Docker Compose stack
	$(COMPOSE) down

docs: ## Run the documentation site on :4321
	$(NPM) --prefix webdocs install
	$(NPM) --prefix webdocs run dev

clean: ## Remove build output
	$(MVN) clean
	rm -rf frontend/dist
