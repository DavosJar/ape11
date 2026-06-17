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
	docker stack deploy -c docker-compose.yml cluster

master: swarm-init registry label build deploy

redeploy:
	docker build -t $(REGISTRY)/app:1.0 .
	docker push $(REGISTRY)/app:1.0
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

logs:
	docker service logs -f cluster_app

stop:
	docker stack rm cluster

reset:
	docker swarm leave --force
