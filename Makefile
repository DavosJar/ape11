include .env
export

# =================== MASTER ===================

swarm-init:
	docker swarm init

registry:
	docker service create --name registry --publish 5000:5000 registry:2

label:
	docker node update --label-add node_id=1 $(NODO_1)


token:
	docker swarm join-token worker

build:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0

deploy:
	docker service create \
		--name cluster_app \
		--mode global \
		--network host \
		--env NODE_HOSTNAME="{{.Node.Hostname}}" \
		--env NODO_1=$(NODO_1) \
		--env NODO_2=$(NODO_2) \
		--env NODO_3=$(NODO_3) \
		--env NODO_4=$(NODO_4) \
		--env NODO_5=$(NODO_5) \
		$(REGISTRY)/app:1.0

master: swarm-init registry label build deploy

redeploy:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0
	docker service rm cluster_app 2>/dev/null || true
	sleep 3
	docker service create \
		--name cluster_app \
		--mode global \
		--network host \
		--env NODE_HOSTNAME="{{.Node.Hostname}}" \
		--env NODO_1=$(NODO_1) \
		--env NODO_2=$(NODO_2) \
		$(REGISTRY)/app:1.0

# =================== SIMULACION LOCAL ===================

deploy_sim:
	docker compose -f docker-compose.dev.yml build -q
	docker compose -f docker-compose.dev.yml up -d
	@echo "5 procesos desplegados. Usa 'make logs_sim' para ver logs"

logs_sim:
	docker compose -f docker-compose.dev.yml logs -f

stop_sim:
	docker compose -f docker-compose.dev.yml down

status_sim:
	docker compose -f docker-compose.dev.yml ps

# =================== WORKER ===================

worker-registry:
	echo '{"insecure-registries": ["$(REGISTRY)"]}' | sudo tee /etc/docker/daemon.json
	sudo systemctl restart docker

worker-join:
	docker swarm join --token $(SWARM_TOKEN) $(MASTER):2377

worker: worker-registry worker-join

# =================== UTILS ===================

clean-service:
	docker service rm cluster_app 2>/dev/null || true
	sleep 3

status:
	docker node ls

logs:
	docker service logs -f cluster_app

stop:
	docker stack rm cluster

reset:
	docker swarm leave --force
