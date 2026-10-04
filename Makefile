# Developer shortcuts (Phase 3 (7)). Everything here is local-only; see scripts/dev.sh for the details.
.PHONY: dev reset-dev clean-test-data e2e test

dev:              ## start database, backend (local profile) and frontend
	./scripts/dev.sh

reset-dev:        ## wipe esg_dev, migrate and reseed the demo tenant
	./scripts/dev.sh reset-dev

clean-test-data:  ## soft-delete records marked as test data (is_test) in the demo tenant
	./scripts/dev.sh clean-test-data

e2e:              ## Cypress journeys on a fresh esg_e2e database
	./scripts/e2e.sh

test:             ## backend and frontend unit/integration tests
	cd esg-backend && ./mvnw -B verify
	cd esg-frontend && npm test -- --run
