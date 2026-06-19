include .env
export

# =================== MASTER ===================

swarm-init:
	docker swarm init

registry:
	docker service create --name registry --publish 5000:5000 registry:2

token:
	docker swarm join-token worker


# =================== ETIQUETAR NODOS CON SUS IPs ===================
# Ejecutar DESPUÉS de que todos los workers se hayan unido al swarm
# Verificar con: docker node ls

label-sebastian:
	docker node update --label-add ip=$(NODO_1) sebastian

label-david:
	docker node update --label-add ip=$(NODO_2) david-HP-Laptop-14-bs0xx

label-stiven:
	docker node update --label-add ip=$(NODO_3) stiven

label-gabriel:
	docker node update --label-add ip=$(NODO_4) gabriel

labels: label-sebastian label-david label-stiven label-gabriel
	@echo "✓ Nodos etiquetados con sus IPs"

show-labels:
	docker node ls -q | xargs docker node inspect --format '{{.Description.Hostname}} → {{.Spec.Labels.ip}}'


# =================== BUILD Y DEPLOY ===================

build:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0

deploy:
	docker service create \
		--name cluster_app \
		--mode global \
		--network host \
		--env NODE_IP="{{.Node.Labels.ip}}" \
		--env NODO_1=$(NODO_1) \
		--env NODO_2=$(NODO_2) \
		--env NODO_3=$(NODO_3) \
		--env NODO_4=$(NODO_4) \
		--env NODO_5=$(NODO_5) \
		$(REGISTRY)/app:1.0

master: swarm-init registry build labels deploy

redeploy:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0
	docker service rm cluster_app 2>/dev/null || true
	sleep 3
	docker service create \
		--name cluster_app \
		--mode global \
		--network host \
		--env NODE_IP="{{.Node.Labels.ip}}" \
		--env NODO_1=$(NODO_1) \
		--env NODO_2=$(NODO_2) \
		--env NODO_3=$(NODO_3) \
		--env NODO_4=$(NODO_4) \
		--env NODO_5=$(NODO_5) \
		$(REGISTRY)/app:1.0

# =================== SIMULACION LOCAL ===================

deploy_sim:
	docker compose -f docker-compose.dev.yml build
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
