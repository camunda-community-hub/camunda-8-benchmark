IMAGE=gcr.io/camunda-researchanddevelopment/falko-camunda-8-benchmark:0.0.1-SNAPSHOT

all:
	mvn spring-boot:build-image -Dspring-boot.build-image.imageName=$(IMAGE)

install:
	docker push $(IMAGE)
