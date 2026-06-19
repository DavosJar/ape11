include .env
export

# =================== MASTER ===================

swarm-init:
	docker swarm init --advertise-addr $(MASTER)

registry:
	docker service create --name registry --publish 5000:5000 registry:2

token:
	docker swarm join-token worker

# =================== ETIQUETAR NODOS CON SUS IPs ===================

label-alexis:
	docker node update --label-add ip=$(NODO_1) alexis-work

label-sebastian:
	docker node update --label-add ip=$(NODO_2) david-HP-Laptop-14-bs0xx

labels: label-alexis label-sebastian
	@echo "✓ Nodos etiquetados con sus IPs"

show-labels:
	docker node ls -q | xargs docker node inspect --format '{{.Description.Hostname}} → {{.Spec.Labels.ip}}'

# =================== BUILD Y DEPLOY ===================

build:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0

deploy:
	docker stack deploy -c docker-compose.yml cluster

master: swarm-init registry build deploy

redeploy:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0
	docker stack rm cluster 2>/dev/null || true
	sleep 5
	docker stack deploy -c docker-compose.yml cluster

# =================== WORKER ===================

worker-registry:
	echo '{"insecure-registries": ["$(REGISTRY)"]}' | sudo tee /etc/docker/daemon.json
	sudo systemctl restart docker

worker-join:
	docker swarm join --token $(SWARM_TOKEN) $(MASTER):2377

worker: worker-registry worker-join

# =================== UTILS ===================

status:
	docker node ls
	docker service ls

logs:
	docker service logs -f cluster_app

stop:
	docker stack rm cluster

reset:
	docker swarm leave --force